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

    private static final double MAX_RADIUS_KM = 100.0;

    private static final int MAX_LIMIT = 50;


    private final ShelterSearchService shelterSearchService;


    // ============================================================
    // 보호소 검색
    // ============================================================
    //
    // 예:
    //
    // 전체
    // GET /api/shelters/search
    //
    // 지역
    // GET /api/shelters/search?location=서울
    // GET /api/shelters/search?location=경기
    // GET /api/shelters/search?location=고양
    //
    // GPS
    // GET /api/shelters/search
    //     ?lat=37.65
    //     &lng=126.83
    //     &radius_km=10
    //
    // ============================================================

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShelterSearchResponse> search(

            @RequestParam(required = false)
            String location,

            @RequestParam(required = false)
            Double lat,

            @RequestParam(required = false)
            Double lng,

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

        /*
         * 기존에는 location도 없고 GPS도 없으면
         * INVALID_INPUT을 반환했다.
         *
         * 하지만 파라미터가 없는 요청은
         * "전체 보호소 조회"로 사용한다.
         */
        validateSearchParameters(
                lat,
                lng,
                radiusKm,
                limit,
                offset
        );


        ShelterSearchResponse response =
                shelterSearchService.search(
                        location,
                        lat,
                        lng,
                        radiusKm,
                        sortBy,
                        limit,
                        offset
                );


        return ResponseEntity.ok(
                response
        );
    }


    // ============================================================
    // 검색 파라미터 검증
    // ============================================================

    private void validateSearchParameters(

            Double lat,
            Double lng,
            Double radiusKm,
            Integer limit,
            Integer offset

    ) {

        boolean hasLatitude =
                lat != null;

        boolean hasLongitude =
                lng != null;


        // 위도/경도는 둘 다 있거나 둘 다 없어야 한다.
        if (hasLatitude != hasLongitude) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        // GPS 범위 검증
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


        // 반경 검증
        if (radiusKm != null && (

                !isFinite(radiusKm)

                || radiusKm <= 0
                || radiusKm > MAX_RADIUS_KM

        )) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        // limit 검증
        if (limit != null
                && (
                    limit <= 0
                    || limit > MAX_LIMIT
                )) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        // offset 검증
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