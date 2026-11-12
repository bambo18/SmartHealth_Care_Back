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
import lombok.extern.slf4j.Slf4j;


@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ShelterSearchService {

    /*
     * 좌표가 없을 때 응답 center에 채우는 값.
     *
     * 조회 범위에는 영향이 없다.
     */
    private static final double DEFAULT_CENTER_LAT =
            37.5665;

    private static final double DEFAULT_CENTER_LNG =
            126.9780;


    /*
     * 반경으로 조회 범위를 정하지 않는다.
     *
     * 응답 스키마 호환을 위해 필드는 유지하고
     * 의미 없는 값임을 나타내기 위해 0을 보낸다.
     */
    private static final double UNUSED_RADIUS_KM =
            0.0;


    /*
     * 관할구역 문자열 포맷.
     *
     * 예:
     *
     * 경기도: 구리시, 김포시, 파주시 | 서울특별시: 강남구, 강동구
     *
     * 시·도 단위는 파이프로, 시·군·구 단위는 쉼표로 구분된다.
     */
    private static final String JURISDICTION_PROVINCE_DELIMITER =
            "\\|";

    private static final String JURISDICTION_DISTRICT_DELIMITER =
            ",";

    private static final String JURISDICTION_PROVINCE_SEPARATOR =
            ":";


    private final ShelterSearchRepository shelterRepository;


    // ============================================================
    // 보호소 검색
    // ============================================================
    //
    // 조회 범위는 행정구역이 정하고,
    // 좌표는 거리 계산과 정렬에만 쓴다.
    //
    // province, district 모두 없음  -> 전체 보호소
    // province만 있음               -> 해당 시·도 전체
    // province + district           -> 해당 시·군·구 관할
    //
    // ============================================================

    public ShelterSearchResponse search(

            String locationText,

            String provinceText,
            String districtText,

            Double lat,
            Double lng,

            String sortBy,

            Integer limit,
            Integer offset

    ) {

        String location =
                normalizeLocation(
                        locationText
                );


        String province =
                normalizeProvince(
                        provinceText
                );


        String district =
                normalizeDistrict(
                        districtText
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
        // DB 조회 — 범위는 행정구역만으로 결정한다
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
        // 2. province + district -> 시·군·구 관할
        // ========================================================
        //
        // 관외 위탁 보호소도 jurisdiction으로 포함된다.
        //
        // ========================================================

        else if (province != null
                && district != null) {

            dbShelters =
                    filterByRegion(
                            shelterRepository
                                    .searchActiveByAdministrativeRegion(
                                            province,
                                            district
                                    ),
                            province,
                            district
                    );
        }


        // ========================================================
        // 3. province만 -> 시·도 전체
        // ========================================================

        else if (province != null) {

            dbShelters =
                    filterByRegion(
                            shelterRepository
                                    .searchActiveByProvince(
                                            province
                                    ),
                            province,
                            null
                    );
        }


        // ========================================================
        // 4. district만 (프론트가 province를 빠뜨린 경우 방어)
        // ========================================================

        else if (district != null) {

            dbShelters =
                    filterByRegion(
                            shelterRepository
                                    .searchActiveByDistrict(
                                            district
                                    ),
                            null,
                            district
                    );
        }


        // ========================================================
        // 5. 둘 다 없음 -> 전체 보호소
        // ========================================================

        else {

            dbShelters =
                    shelterRepository
                            .findAllActive();
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
             * 좌표가 있으면 거리를 계산한다.
             *
             * 표시용/정렬용이며
             * 이 값으로 결과를 걸러내지는 않는다.
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

                UNUSED_RADIUS_KM,

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
    // 행정구역 정밀 필터
    // ============================================================
    //
    // DB 쿼리는 인덱스를 쓰기 위한 1차 필터이고,
    // ILIKE 부분 일치 때문에 두 종류의 오탐이 섞인다.
    //
    // 1. 세그먼트 교차
    //
    //    jurisdiction = 경기도: 파주시 | 서울특별시: 강남구
    //
    //    province=경기도, district=강남구로 조회하면
    //    두 조건이 서로 다른 세그먼트에 걸려 통과한다.
    //
    // 2. 부분 문자열
    //
    //    district=서구로 조회하면 강서구가 걸린다.
    //
    // 여기서 걸러낸다.
    //
    // ============================================================

    private List<Shelter> filterByRegion(

            List<Shelter> candidates,

            String province,
            String district

    ) {

        List<Shelter> matched =
                new ArrayList<>();


        for (Shelter shelter : candidates) {

            if (matchesRegion(
                    shelter,
                    province,
                    district
            )) {

                matched.add(
                        shelter
                );
            }
        }


        if (matched.size() != candidates.size()) {

            log.debug(
                    "Shelter region filter: province={}, district={}, candidates={}, matched={}",
                    province,
                    district,
                    candidates.size(),
                    matched.size()
            );
        }


        return matched;
    }


    private boolean matchesRegion(

            Shelter shelter,

            String province,
            String district

    ) {

        /*
         * 소재지 컬럼으로 맞는 경우
         */
        boolean provinceOk =
                province == null
                        || provinceMatches(
                                shelter.getProvince(),
                                province
                        );


        boolean districtOk =
                district == null
                        || districtMatches(
                                shelter.getDistrict(),
                                district
                        );


        if (provinceOk && districtOk) {
            return true;
        }


        /*
         * 관외 위탁 보호소는 소재지가 다르므로
         * 관할구역으로 한 번 더 본다.
         */
        return jurisdictionCovers(
                shelter.getJurisdiction(),
                province,
                district
        );
    }


    // ============================================================
    // 관할구역 포함 여부
    // ============================================================
    //
    // 시·도 세그먼트로 끊은 뒤 그 안에서
    // province와 district가 동시에 맞을 때만 통과시킨다.
    //
    // ============================================================

    private boolean jurisdictionCovers(

            String jurisdiction,

            String province,
            String district

    ) {

        if (jurisdiction == null
                || jurisdiction.isBlank()) {

            return false;
        }


        for (String segment : jurisdiction.split(
                JURISDICTION_PROVINCE_DELIMITER
        )) {

            if (segment.isBlank()) {
                continue;
            }


            int separatorIndex =
                    segment.indexOf(
                            JURISDICTION_PROVINCE_SEPARATOR
                    );


            String segmentProvince =
                    separatorIndex >= 0
                            ? segment.substring(
                                    0,
                                    separatorIndex
                            )
                            : segment;


            String segmentDistricts =
                    separatorIndex >= 0
                            ? segment.substring(
                                    separatorIndex + 1
                            )
                            : "";


            /*
             * 세그먼트의 시·도가 다르면 볼 필요가 없다.
             */
            if (province != null
                    && !provinceMatches(
                            segmentProvince,
                            province
                    )) {

                continue;
            }


            /*
             * 시·군·구 조건이 없으면
             * 시·도만 맞아도 관할에 포함된다.
             */
            if (district == null) {
                return true;
            }


            for (String segmentDistrict : segmentDistricts.split(
                    JURISDICTION_DISTRICT_DELIMITER
            )) {

                if (districtMatches(
                        segmentDistrict,
                        district
                )) {

                    return true;
                }
            }
        }


        return false;
    }


    // ============================================================
    // 시·도 비교
    // ============================================================
    //
    // 프론트가 경기 / 경기도처럼
    // 긴 이름과 짧은 이름을 섞어 보낼 수 있어
    // 양방향 포함으로 비교한다.
    //
    // 시·도 17개는 서로 포함 관계가 없으므로
    // 이 정도 느슨함이 오탐을 만들지 않는다.
    //
    // ============================================================

    private boolean provinceMatches(

            String stored,
            String requested

    ) {

        String storedValue =
                normalizeProvince(
                        stored
                );


        String requestedValue =
                normalizeProvince(
                        requested
                );


        if (storedValue == null
                || requestedValue == null) {

            return false;
        }


        return storedValue.contains(requestedValue)
                || requestedValue.contains(storedValue);
    }


    // ============================================================
    // 시·군·구 비교
    // ============================================================
    //
    // 서구가 강서구에 걸리는 것을 막기 위해
    // 부분 문자열이 아니라 정확히 같은 이름만 통과시킨다.
    //
    // ============================================================

    private boolean districtMatches(

            String stored,
            String requested

    ) {

        String storedValue =
                normalizeDistrict(
                        stored
                );


        String requestedValue =
                normalizeDistrict(
                        requested
                );


        if (storedValue == null
                || requestedValue == null) {

            return false;
        }


        return storedValue.equals(
                requestedValue
        );
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


        if (value.isBlank()
                || value.equals("전체")
                || value.equalsIgnoreCase("all")) {

            return null;
        }


        return value;
    }


    // ============================================================
    // 시·군·구 정규화
    // ============================================================
    //
    // 프론트/관할 문자열 예:
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


        if (value.isBlank()
                || value.equals("전체")
                || value.equalsIgnoreCase("all")) {

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
