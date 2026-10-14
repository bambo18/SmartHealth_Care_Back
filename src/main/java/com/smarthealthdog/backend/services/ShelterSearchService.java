package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.clients.naver.NaverReverseGeocodingClient;
import com.smarthealthdog.backend.clients.naver.NaverReverseGeocodingClient.AdministrativeRegion;
import com.smarthealthdog.backend.domain.Shelter;
import com.smarthealthdog.backend.dto.shelter.ShelterItem;
import com.smarthealthdog.backend.dto.shelter.ShelterItem.Location;
import com.smarthealthdog.backend.dto.shelter.ShelterSearchResponse;
import com.smarthealthdog.backend.repositories.ShelterSearchRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ShelterSearchService {

    private static final double DEFAULT_CENTER_LAT =
            37.5665;

    private static final double DEFAULT_CENTER_LNG =
            126.9780;


    private static final double DEFAULT_RADIUS_KM =
            2.0;


    /*
     * 현재 프론트에서 시/군/구 선택 시
     * radius_km = 100을 전달한다.
     */
    private static final double REGION_RADIUS_KM =
            100.0;


    /*
     * 현재 프론트 전체 보호소 요청:
     *
     * radius_km = 1000
     * limit = 1000
     */
    private static final double ALL_RADIUS_KM =
            1000.0;


    private static final int ALL_LIMIT =
            1000;


    private final ShelterSearchRepository shelterRepository;


    private final NaverReverseGeocodingClient
            naverReverseGeocodingClient;


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

        String location =
                normalizeLocation(
                        locationText
                );


        boolean hasCoordinates =
                lat != null
                        && lng != null;


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
                                1000
                        )

                        : 20;


        int pageOffset =
                offset != null
                        ? offset
                        : 0;


        // --------------------------------------------------------
        // 어떤 검색 방식인지 판단
        // --------------------------------------------------------

        boolean allRequest =
                isAllRequest(
                        radiusKm,
                        limit
                );


        boolean regionRequest =
                !allRequest
                        && hasCoordinates
                        && isRegionRequest(
                                radiusKm
                        );


        /*
         * true:
         *
         * GPS 기반 일반 거리검색일 경우에만
         * radius 거리 필터를 적용한다.
         *
         * false:
         *
         * 전체 / 행정구역 검색에서는
         * 반경 필터를 하지 않는다.
         */
        boolean applyDistanceFilter =
                false;


        // --------------------------------------------------------
        // DB 조회
        // --------------------------------------------------------

        List<Shelter> dbShelters;


        // ========================================================
        // 1. location을 직접 전달한 경우
        // ========================================================

        if (location != null) {

            dbShelters =
                    shelterRepository
                            .searchActiveByLocation(
                                    location
                            );
        }


        // ========================================================
        // 2. 전체 보호소
        // ========================================================
        //
        // 현재 프론트:
        //
        // radius_km = 1000
        // limit = 1000
        //
        // ========================================================

        else if (allRequest) {

            dbShelters =
                    shelterRepository
                            .findAllActive();
        }


        // ========================================================
        // 3. 시/군/구 선택
        // ========================================================
        //
        // 현재 프론트:
        //
        // radius_km = 100
        //
        // 좌표를 Reverse Geocoding해서
        // 실제 행정구역으로 변환한다.
        //
        // ========================================================

        else if (regionRequest) {

            dbShelters =
                    searchByCoordinatesAsRegion(
                            lat,
                            lng
                    );
        }


        // ========================================================
        // 4. 일반 GPS 거리검색
        // ========================================================

        else {

            dbShelters =
                    shelterRepository
                            .findAllActive();


            applyDistanceFilter =
                    hasCoordinates;
        }


        // --------------------------------------------------------
        // DTO 변환
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
             * 좌표가 있으면 거리값 자체는 계산한다.
             *
             * 지역검색에서는 거리값을 표시용/정렬용으로만 사용하고
             * 100km 반경 필터는 하지 않는다.
             */
            if (
                    hasCoordinates
                    && shelterLat != null
                    && shelterLng != null
            ) {

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
             * 일반 GPS 검색인 경우에만
             * 실제 거리 반경 필터.
             */
            if (applyDistanceFilter) {

                if (distanceM == null) {
                    continue;
                }


                if (
                        distanceM
                                > radius * 1000
                ) {

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

                            null,

                            null,

                            null,

                            null,

                            null
                    );


            items.add(
                    item
            );
        }


        // --------------------------------------------------------
        // 정렬
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


        int total =
                items.size();


        // --------------------------------------------------------
        // Paging
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
    // 좌표 → 행정구역 → 보호소 조회
    // ============================================================

    private List<Shelter> searchByCoordinatesAsRegion(

            double lat,
            double lng

    ) {

        try {

            AdministrativeRegion region =
                    naverReverseGeocodingClient
                            .reverseGeocodeRegion(
                                    lat,
                                    lng
                            );


            String province =
                    normalizeProvince(
                            region.province()
                    );


            String district =
                    normalizeDistrict(
                            region.district()
                    );


            log.info(
                    "Shelter region search: lat={}, lng={}, province={}, district={}",
                    lat,
                    lng,
                    province,
                    district
            );


            if (
                    province != null
                    && district != null
            ) {

                return shelterRepository
                        .searchActiveByAdministrativeRegion(
                                province,
                                district
                        );
            }


            if (district != null) {

                return shelterRepository
                        .searchActiveByDistrict(
                                district
                        );
            }


            if (province != null) {

                return shelterRepository
                        .searchActiveByProvince(
                                province
                        );
            }


            /*
             * 행정구역을 얻지 못했다면
             * 잘못된 지역을 보여주는 것보다
             * 빈 결과를 반환한다.
             */
            return List.of();

        } catch (Exception e) {

            log.warn(
                    "Failed to reverse geocode shelter search coordinates. lat={}, lng={}",
                    lat,
                    lng,
                    e
            );


            /*
             * 네이버 API 오류 때문에
             * 서버 전체가 500이 되는 것을 방지한다.
             */
            return List.of();
        }
    }


    // ============================================================
    // 전체 요청 판별
    // ============================================================

    private boolean isAllRequest(

            Double radiusKm,
            Integer limit

    ) {

        return radiusKm != null
                && radiusKm >= ALL_RADIUS_KM

                && limit != null
                && limit >= ALL_LIMIT;
    }


    // ============================================================
    // 지역 선택 요청 판별
    // ============================================================

    private boolean isRegionRequest(
            Double radiusKm
    ) {

        if (radiusKm == null) {
            return false;
        }


        return Math.abs(
                radiusKm
                        - REGION_RADIUS_KM
        ) < 0.0001;
    }


    // ============================================================
    // location 문자열 정리
    // ============================================================

    private String normalizeLocation(
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
    // 시·도 정규화
    // ============================================================

    private String normalizeProvince(
            String province
    ) {

        if (province == null) {
            return null;
        }


        String value =
                province.trim();


        return value.isBlank()
                ? null
                : value;
    }


    // ============================================================
    // 시·군·구 정규화
    // ============================================================
    //
    // NAVER 응답 예:
    //
    // 고양시 덕양구
    // 수원시 영통구
    // 성남시 분당구
    //
    // DB에는:
    //
    // 고양시
    // 수원시
    // 성남시
    //
    // 로 저장되어 있으므로 첫 번째 "시" 단위로 통일한다.
    //
    // 서울 양천구 / 마포구처럼
    // 단독 구 이름은 그대로 유지한다.
    //
    // ============================================================

    private String normalizeDistrict(
            String district
    ) {

        if (district == null) {
            return null;
        }


        String value =
                district.trim();


        if (value.isBlank()) {
            return null;
        }


        String[] parts =
                value.split(
                        "\\s+"
                );


        if (
                parts.length >= 2
                && parts[0].endsWith("시")
        ) {

            return parts[0];
        }


        return value;
    }


    private Double toDouble(
            BigDecimal value
    ) {

        return value != null
                ? value.doubleValue()
                : null;
    }


    // ============================================================
    // Haversine
    // ============================================================

    private double haversineMeters(

            double lat1,
            double lon1,

            double lat2,
            double lon2

    ) {

        double earthRadius =
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

                Math.sin(
                        dLat / 2
                )

                * Math.sin(
                        dLat / 2
                )

                + Math.cos(
                        Math.toRadians(lat1)
                )

                * Math.cos(
                        Math.toRadians(lat2)
                )

                * Math.sin(
                        dLon / 2
                )

                * Math.sin(
                        dLon / 2
                );


        double c =
                2
                * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );


        return earthRadius * c;
    }
}