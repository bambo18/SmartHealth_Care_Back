package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Shelter;
import com.smarthealthdog.backend.dto.shelter.ShelterItem;
import com.smarthealthdog.backend.dto.shelter.ShelterItem.Location;
import com.smarthealthdog.backend.dto.shelter.ShelterSearchResponse;
import com.smarthealthdog.backend.repositories.ShelterSearchRepository;

import lombok.RequiredArgsConstructor;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ShelterSearchService {

    /*
     * ShelterSearchResponse의 center가
     * double primitive 타입이기 때문에
     * GPS가 없는 경우 응답 형식 유지를 위한 기본 좌표를 사용한다.
     *
     * 중요:
     * 이 좌표는 GPS가 없는 경우 거리 필터에 사용하지 않는다.
     */
    private static final double DEFAULT_CENTER_LAT =
            37.5665;

    private static final double DEFAULT_CENTER_LNG =
            126.9780;

    private static final double DEFAULT_RADIUS_KM =
            2.0;


    private final ShelterSearchRepository shelterRepository;


    // ============================================================
    // 보호소 검색
    // ============================================================

    public ShelterSearchResponse search(

            String locationText,

            Double lat,
            Double lng,

            Double radiusKm,

            String sortBy,

            Integer limit,
            Integer offset

    ) {

        // --------------------------------------------------------
        // 1. 검색 조건 정리
        // --------------------------------------------------------

        String location =
                normalizeLocation(
                        locationText
                );


        boolean hasCoordinates =
                lat != null
                && lng != null;


        /*
         * GPS가 있는 경우:
         * 실제 사용자 좌표 사용
         *
         * GPS가 없는 경우:
         * 응답 center 필드를 채우기 위한 값일 뿐이며
         * 거리 필터에는 사용하지 않는다.
         */
        double baseLat =
                hasCoordinates
                        ? lat
                        : DEFAULT_CENTER_LAT;


        double baseLng =
                hasCoordinates
                        ? lng
                        : DEFAULT_CENTER_LNG;


        double radius =
                radiusKm != null
                        ? radiusKm
                        : DEFAULT_RADIUS_KM;


        String sort =
                sortBy != null
                        && !sortBy.isBlank()

                        ? sortBy.trim()
                        : "distance";


        int pageSize =
                limit != null
                        ? Math.min(
                                limit,
                                50
                        )
                        : 20;


        int pageOffset =
                offset != null
                        ? offset
                        : 0;


        // --------------------------------------------------------
        // 2. DB 보호소 조회
        // --------------------------------------------------------

        List<Shelter> dbShelters;


        /*
         * location이 없으면:
         *
         * 전체 보호소 조회
         *
         * location이 있으면:
         *
         * province
         * district
         * address
         * name
         *
         * 기준 지역 검색
         */
        if (location == null) {

            dbShelters =
                    shelterRepository
                            .findAllActive();

        } else {

            dbShelters =
                    shelterRepository
                            .searchActiveByLocation(
                                    location
                            );
        }


        // --------------------------------------------------------
        // 3. Shelter Entity → ShelterItem 변환
        // --------------------------------------------------------

        List<ShelterItem> items =
                new ArrayList<>();


        for (Shelter shelter : dbShelters) {

            Double shelterLat =
                    toDouble(
                            shelter.getLatitude()
                    );


            Double shelterLng =
                    toDouble(
                            shelter.getLongitude()
                    );


            Integer distanceM =
                    null;


            /*
             * 실제 GPS 좌표가 있을 때만
             * 사용자와 보호소 사이 거리를 계산한다.
             *
             * GPS가 없을 때 서울시청 기준의
             * 가짜 거리를 만들지 않는다.
             */
            if (hasCoordinates
                    && shelterLat != null
                    && shelterLng != null) {

                distanceM =
                        (int) Math.round(
                                haversineMeters(
                                        baseLat,
                                        baseLng,
                                        shelterLat,
                                        shelterLng
                                )
                        );
            }


            /*
             * GPS 검색인 경우에만 반경 필터 적용
             *
             * 좌표가 없는 보호소는 정확한 거리 판정이
             * 불가능하므로 GPS 반경 검색에서는 제외한다.
             *
             * 지역검색 / 전체검색에서는 좌표가 없어도
             * 정상적으로 표시한다.
             */
            if (hasCoordinates) {

                if (distanceM == null) {
                    continue;
                }


                if (distanceM
                        > radius * 1000) {

                    continue;
                }
            }


            ShelterItem item =
                    new ShelterItem(

                            shelter.getId(),

                            shelter.getName(),

                            shelter.getAddress(),

                            shelter.getPhoneNumber(),

                            shelterLat != null
                                    && shelterLng != null

                                    ? new Location(
                                            shelterLat,
                                            shelterLng
                                    )

                                    : null,

                            distanceM,

                            null,       // rating

                            null,       // review_count

                            null,       // website_url

                            null,       // place_url

                            null        // open_now
                    );


            items.add(
                    item
            );
        }


        // --------------------------------------------------------
        // 4. 정렬
        // --------------------------------------------------------

        items.sort(

                switch (sort) {

                    case "rating" ->

                            Comparator
                                    .comparing(
                                            (ShelterItem item) ->
                                                    item.rating() == null
                                                            ? 0.0
                                                            : item.rating()
                                    )
                                    .reversed()

                                    .thenComparing(
                                            item ->
                                                    item.review_count() == null
                                                            ? 0
                                                            : item.review_count(),
                                            Comparator.reverseOrder()
                                    );


                    case "review_count" ->

                            Comparator
                                    .comparing(
                                            (ShelterItem item) ->
                                                    item.review_count() == null
                                                            ? 0
                                                            : item.review_count(),
                                            Comparator.reverseOrder()
                                    )

                                    .thenComparing(
                                            item ->
                                                    item.rating() == null
                                                            ? 0.0
                                                            : item.rating(),
                                            Comparator.reverseOrder()
                                    );


                    /*
                     * 기존 API 기본값
                     */
                    default ->

                            Comparator
                                    .comparing(
                                            (ShelterItem item) ->
                                                    item.distance_m() == null
                                                            ? Integer.MAX_VALUE
                                                            : item.distance_m()
                                    );
                }
        );


        // --------------------------------------------------------
        // 5. 전체 검색 결과 개수
        // --------------------------------------------------------

        int total =
                items.size();


        // --------------------------------------------------------
        // 6. 페이징
        // --------------------------------------------------------

        int fromIndex =
                Math.min(
                        pageOffset,
                        items.size()
                );


        int toIndex =
                Math.min(
                        items.size(),
                        pageOffset + pageSize
                );


        List<ShelterItem> paged =
                items.subList(
                        fromIndex,
                        toIndex
                );


        // --------------------------------------------------------
        // 7. 응답
        // --------------------------------------------------------

        return new ShelterSearchResponse(

                new ShelterSearchResponse.Center(
                        baseLat,
                        baseLng
                ),

                radius,

                sort,

                total,

                paged,

                new ShelterSearchResponse.PageMeta(
                        pageSize,
                        pageOffset
                )
        );
    }


    // ============================================================
    // location 정규화
    // ============================================================

    private static String normalizeLocation(
            String location
    ) {

        if (location == null) {
            return null;
        }


        String value =
                location.trim();


        if (value.isBlank()) {
            return null;
        }


        /*
         * 프론트에서 전체 선택 시
         * 문자열을 보내는 경우까지 대응
         */
        if (
                value.equals("전체")
                || value.equals("전체 보호소")
                || value.equals("전체보호소")
                || value.equals("전국")
                || value.equalsIgnoreCase("all")
        ) {

            return null;
        }


        return value;
    }


    // ============================================================
    // BigDecimal → Double
    // ============================================================

    private static Double toDouble(
            BigDecimal value
    ) {

        return value != null
                ? value.doubleValue()
                : null;
    }


    // ============================================================
    // Haversine 거리 계산
    // ============================================================

    private static double haversineMeters(

            double lat1,
            double lon1,

            double lat2,
            double lon2

    ) {

        double earthRadiusM =
                6_371_000.0;


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

                        + Math.cos(
                                Math.toRadians(lat1)
                        )

                        * Math.cos(
                                Math.toRadians(lat2)
                        )

                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);


        double c =
                2
                * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );


        return earthRadiusM * c;
    }
}