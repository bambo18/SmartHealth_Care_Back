package com.smarthealthdog.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.shelter.ShelterSearchResponse;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.services.ShelterSearchService;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/shelters")
@RequiredArgsConstructor
public class ShelterSearchController {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;

    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;


    /*
     * 현재 프론트 전체 보호소 요청:
     *
     * limit = 1000
     */
    private static final int MAX_LIMIT = 1000;


    private final ShelterSearchService shelterSearchService;


    // ============================================================
    // 보호소 검색
    // ============================================================
    //
    // 조회 범위는 행정구역이 정한다.
    //
    // province, district 모두 없음  -> 전체 보호소
    // province만 있음               -> 해당 시·도 전체
    // province + district           -> 해당 시·군·구 관할
    //
    // lat / lng는 범위를 좁히지 않는다.
    // 거리 계산과 거리순 정렬에만 쓰인다.
    //
    // ============================================================

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShelterSearchResponse> search(

            @RequestParam(required = false)
            String location,

            /*
             * 사용자가 실제로 선택한 행정구역.
             *
             * 좌표를 역지오코딩해서 추측하지 않고
             * 선택값을 그대로 받는다.
             */
            @RequestParam(required = false)
            String province,

            @RequestParam(required = false)
            String district,

            @RequestParam(required = false)
            Double lat,

            @RequestParam(required = false)
            Double lng,

            /*
             * 더 이상 조회 범위에 사용하지 않는다.
             *
             * radius_km = 100 / 1000으로 지역 단계를 구분하던
             * 방식은 제거되었다.
             *
             * 구버전 프론트가 보내도 400이 되지 않도록
             * 받기만 하고 무시한다.
             */
            @RequestParam(
                    name = "radius_km",
                    required = false
            )
            Double radiusKm,

            @RequestParam(
                    name = "sort_by",
                    required = false
            )
            String sortBy,

            @RequestParam(required = false)
            Integer limit,

            @RequestParam(required = false)
            Integer offset

    ) {

        validateSearchParameters(
                lat,
                lng,
                limit,
                offset
        );


        ShelterSearchResponse response =
                shelterSearchService.search(
                        location,
                        province,
                        district,
                        lat,
                        lng,
                        sortBy,
                        limit,
                        offset
                );


        return ResponseEntity.ok(
                response
        );
    }


    private void validateSearchParameters(

            Double lat,
            Double lng,
            Integer limit,
            Integer offset

    ) {

        boolean hasLatitude =
                lat != null;

        boolean hasLongitude =
                lng != null;


        /*
         * 위도/경도는 둘 다 있어야 한다.
         */
        if (hasLatitude != hasLongitude) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        /*
         * 좌표 범위
         */
        if (hasLatitude && (

                !isFinite(lat)
                || !isFinite(lng)

                || lat < MIN_LATITUDE
                || lat > MAX_LATITUDE

                || lng < MIN_LONGITUDE
                || lng > MAX_LONGITUDE

        )) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        /*
         * limit
         */
        if (limit != null
                && (
                    limit <= 0
                    || limit > MAX_LIMIT
                )) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        /*
         * offset
         */
        if (offset != null
                && offset < 0) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }
    }


    private boolean isFinite(
            Double value
    ) {

        return value != null
                && !value.isNaN()
                && !value.isInfinite();
    }
}
