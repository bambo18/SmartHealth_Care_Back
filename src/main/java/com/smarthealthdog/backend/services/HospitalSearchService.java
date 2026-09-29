package com.smarthealthdog.backend.services;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.clients.naver.NaverLocalSearchClient;
import com.smarthealthdog.backend.clients.naver.NaverReverseGeocodingClient;
import com.smarthealthdog.backend.dto.hospital.HospitalItem;
import com.smarthealthdog.backend.dto.hospital.HospitalItem.Location;
import com.smarthealthdog.backend.dto.hospital.HospitalSearchResponse;
import com.smarthealthdog.backend.dto.hospital.naver.NaverLocalSearchResponse;
import com.smarthealthdog.backend.dto.hospital.naver.NaverReverseGeocodingResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HospitalSearchService {

    private static final double DEFAULT_LAT = 37.5665;
    private static final double DEFAULT_LNG = 126.9780;

    /*
     * NAVER Local Search의 mapx/mapy 좌표값을
     * 실제 경도/위도로 변환하기 위한 값
     */
    private static final double NAVER_COORDINATE_SCALE = 10_000_000.0;

    private final NaverLocalSearchClient naverLocalSearchClient;
    private final NaverReverseGeocodingClient naverReverseGeocodingClient;

    /**
     * NAVER API 기반 병원 검색
     */
    public HospitalSearchResponse search(
            String locationText,
            String query,
            Double lat,
            Double lng,
            Double radiusKm,
            String sortBy,
            Integer limit,
            Integer offset
    ) {

        // 1. 기본값
        double baseLat = lat != null ? lat : DEFAULT_LAT;
        double baseLng = lng != null ? lng : DEFAULT_LNG;

        double radius = radiusKm != null ? radiusKm : 2.0;

        String sort = (sortBy != null && !sortBy.isBlank())
                ? sortBy
                : "distance";

        int pageSize = limit != null
                ? Math.min(limit, 50)
                : 20;

        int pageOffset = offset != null
                ? Math.max(offset, 0)
                : 0;

        /*
         * GPS 좌표가 실제로 전달되었는지 확인.
         *
         * GPS가 있는 경우에만 거리 계산과 반경 필터를 적용한다.
         */
        boolean hasGps = lat != null && lng != null;

        // 2. 검색 지역 결정
        String area = determineSearchArea(
                locationText,
                lat,
                lng
        );

        // 3. 검색어 생성
        String searchQuery = buildSearchQuery(area, query);

        /*
         * NAVER Local Search API 호출
         *
         * Local Search API는 한 번에 최대 5개 결과를 반환하므로
         * 우선 5개를 요청한다.
         */
        NaverLocalSearchResponse response =
                naverLocalSearchClient.search(
                        searchQuery,
                        5,
                        1
                );

        List<HospitalItem> items = new ArrayList<>();

        // 응답이 없거나 items가 없으면 빈 결과 반환
        if (response == null || response.items() == null) {

            return createResponse(
                    baseLat,
                    baseLng,
                    radius,
                    sort,
                    pageSize,
                    pageOffset,
                    items
            );
        }

        // 4. NAVER 결과 → 기존 HospitalItem 변환
        for (NaverLocalSearchResponse.Item naverItem : response.items()) {

            String name = stripHtmlTags(naverItem.title());

            String address = firstNonBlank(
                    naverItem.roadAddress(),
                    naverItem.address()
            );

            Double hospitalLng =
                    parseNaverCoordinate(naverItem.mapx());

            Double hospitalLat =
                    parseNaverCoordinate(naverItem.mapy());

            Integer distanceM = null;

            if (hasGps
                    && hospitalLat != null
                    && hospitalLng != null) {

                distanceM = (int) Math.round(
                        haversineMeters(
                                baseLat,
                                baseLng,
                                hospitalLat,
                                hospitalLng
                        )
                );
            }

            /*
             * GPS 검색인 경우 반경 밖 병원 제거
             */
            if (hasGps
                    && distanceM != null
                    && distanceM > radius * 1000) {

                continue;
            }

            Long hospitalId =
                    createStableHospitalId(
                            name,
                            address,
                            naverItem.mapx(),
                            naverItem.mapy()
                    );

            HospitalItem hospitalItem =
                    new HospitalItem(

                            hospitalId,

                            name,

                            address,

                            blankToNull(
                                    naverItem.telephone()
                            ),

                            (hospitalLat != null
                                    && hospitalLng != null)
                                    ? new Location(
                                            hospitalLat,
                                            hospitalLng
                                    )
                                    : null,

                            distanceM,

                            null,   // rating
                            null,   // review_count

                            blankToNull(
                                    naverItem.link()
                            ),

                            null,   // place_url
                            null    // open_now
                    );

            items.add(hospitalItem);
        }

        // 5. 정렬
        sortItems(items, sort);

        // 6. 페이징
        int fromIndex =
                Math.min(
                        pageOffset,
                        items.size()
                );

        int toIndex =
                Math.min(
                        fromIndex + pageSize,
                        items.size()
                );

        List<HospitalItem> paged =
                new ArrayList<>(
                        items.subList(
                                fromIndex,
                                toIndex
                        )
                );

        // 7. 응답
        return new HospitalSearchResponse(

                new HospitalSearchResponse.Center(
                        baseLat,
                        baseLng
                ),

                radius,

                sort,

                paged.size(),

                paged,

                new HospitalSearchResponse.PageMeta(
                        pageSize,
                        pageOffset
                )
        );
    }

    /**
     * 검색 지역 결정
     *
     * 1순위: location 파라미터
     * 2순위: GPS Reverse Geocoding
     */
    private String determineSearchArea(
            String locationText,
            Double lat,
            Double lng
    ) {

        if (locationText != null
                && !locationText.isBlank()) {

            return locationText.trim();
        }

        if (lat == null || lng == null) {
            return "";
        }

        try {

            NaverReverseGeocodingResponse response =
                    naverReverseGeocodingClient.reverseGeocode(
                            lat,
                            lng
                    );

            if (response == null
                    || response.results() == null
                    || response.results().isEmpty()) {

                return "";
            }

            /*
             * admcode/legalcode 결과 중 첫 번째 사용
             */
            for (NaverReverseGeocodingResponse.Result result
                    : response.results()) {

                if (result == null
                        || result.region() == null) {
                    continue;
                }

                String area1 =
                        getAreaName(
                                result.region().area1()
                        );

                String area2 =
                        getAreaName(
                                result.region().area2()
                        );

                /*
                 * 너무 세부적인 동(area3)까지 넣으면
                 * 검색 범위가 지나치게 좁아질 수 있으므로
                 * 시/도 + 시/군/구까지만 사용.
                 *
                 * 예:
                 * 경기도 고양시 일산동구
                 */
                String area =
                        joinNonBlank(
                                area1,
                                area2
                        );

                if (!area.isBlank()) {
                    return area;
                }
            }

        } catch (Exception e) {

            /*
             * Reverse Geocoding 실패 시
             * 병원 검색 전체가 실패하지 않도록
             * 빈 지역으로 fallback
             */
            return "";
        }

        return "";
    }

    /**
     * NAVER 검색어 생성
     *
     * 예:
     *
     * query 없음
     * → 경기도 고양시 일산동구 동물병원
     *
     * query = "24시"
     * → 경기도 고양시 일산동구 24시 동물병원
     *
     * query = "피부 동물병원"
     * → 경기도 고양시 일산동구 피부 동물병원
     */
    private String buildSearchQuery(
            String area,
            String query
    ) {

        String keyword =
                query != null
                        ? query.trim()
                        : "";

        if (keyword.isBlank()) {

            return joinNonBlank(
                    area,
                    "동물병원"
            );
        }

        if (!keyword.contains("동물병원")) {

            keyword =
                    keyword + " 동물병원";
        }

        return joinNonBlank(
                area,
                keyword
        );
    }

    /**
     * 정렬
     */
    private void sortItems(
            List<HospitalItem> items,
            String sort
    ) {

        items.sort(
                switch (sort) {

                    /*
                     * NAVER Local Search API에는
                     * rating/review_count 정보가 없기 때문에
                     * 해당 값이 존재할 경우에만 의미가 있다.
                     */

                    case "rating" -> Comparator
                            .comparing(
                                    (HospitalItem i) ->
                                            i.rating() == null
                                                    ? 0.0
                                                    : i.rating()
                            )
                            .reversed()
                            .thenComparing(
                                    i ->
                                            i.review_count() == null
                                                    ? 0
                                                    : i.review_count(),
                                    Comparator.reverseOrder()
                            );

                    case "review_count" -> Comparator
                            .comparing(
                                    (HospitalItem i) ->
                                            i.review_count() == null
                                                    ? 0
                                                    : i.review_count(),
                                    Comparator.reverseOrder()
                            )
                            .thenComparing(
                                    i ->
                                            i.rating() == null
                                                    ? 0.0
                                                    : i.rating(),
                                    Comparator.reverseOrder()
                            );

                    default -> Comparator
                            .comparing(
                                    (HospitalItem i) ->
                                            i.distance_m() == null
                                                    ? Integer.MAX_VALUE
                                                    : i.distance_m()
                            );
                }
        );
    }

    /**
     * 빈 응답 생성
     */
    private HospitalSearchResponse createResponse(
            double baseLat,
            double baseLng,
            double radius,
            String sort,
            int pageSize,
            int pageOffset,
            List<HospitalItem> items
    ) {

        return new HospitalSearchResponse(

                new HospitalSearchResponse.Center(
                        baseLat,
                        baseLng
                ),

                radius,

                sort,

                items.size(),

                items,

                new HospitalSearchResponse.PageMeta(
                        pageSize,
                        pageOffset
                )
        );
    }

    /**
     * NAVER mapx/mapy → 실제 좌표
     *
     * 예:
     *
     * 1269710250
     * ↓
     * 126.9710250
     */
    private Double parseNaverCoordinate(
            String coordinate
    ) {

        if (coordinate == null
                || coordinate.isBlank()) {

            return null;
        }

        try {

            return Double.parseDouble(coordinate)
                    / NAVER_COORDINATE_SCALE;

        } catch (NumberFormatException e) {

            return null;
        }
    }

    /**
     * NAVER title의 HTML 태그 제거
     *
     * 예:
     *
     * 누리봄<b>동물병원</b>
     * ↓
     * 누리봄동물병원
     */
    private String stripHtmlTags(
            String value
    ) {

        if (value == null) {
            return null;
        }

        return value
                .replaceAll("<[^>]*>", "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    /**
     * NAVER 결과에는 우리 DB의 hospital_id가 없으므로
     * 병원 이름 + 주소 + 좌표를 기반으로
     * 재현 가능한 Long ID 생성
     */
    private Long createStableHospitalId(
            String name,
            String address,
            String mapx,
            String mapy
    ) {

        String source =
                String.valueOf(name)
                        + "|"
                        + String.valueOf(address)
                        + "|"
                        + String.valueOf(mapx)
                        + "|"
                        + String.valueOf(mapy);

        /*
         * FNV-1a 64 bit
         */
        long hash =
                0xcbf29ce484222325L;

        byte[] bytes =
                source.getBytes(
                        StandardCharsets.UTF_8
                );

        for (byte b : bytes) {

            hash ^= (b & 0xff);

            hash *=
                    0x100000001b3L;
        }

        /*
         * 음수 ID 방지
         */
        return hash & Long.MAX_VALUE;
    }

    private String getAreaName(
            NaverReverseGeocodingResponse.Area area
    ) {

        if (area == null
                || area.name() == null) {

            return "";
        }

        return area.name().trim();
    }

    private String joinNonBlank(
            String... values
    ) {

        List<String> result =
                new ArrayList<>();

        for (String value : values) {

            if (value != null
                    && !value.isBlank()) {

                result.add(
                        value.trim()
                );
            }
        }

        return String.join(
                " ",
                result
        );
    }

    private String firstNonBlank(
            String first,
            String second
    ) {

        if (first != null
                && !first.isBlank()) {

            return first;
        }

        return blankToNull(second);
    }

    private String blankToNull(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }

    /**
     * Haversine 거리 계산
     * 단위: meter
     */
    private static double haversineMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double R = 6371000.0;

        double dLat =
                Math.toRadians(
                        lat2 - lat1
                );

        double dLon =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(dLat / 2)
                        * Math.sin(dLat / 2)
                        +
                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(lat2)
                                )
                                *
                                Math.sin(dLon / 2)
                                *
                                Math.sin(dLon / 2);

        double c =
                2
                        *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(1 - a)
                        );

        return R * c;
    }
}