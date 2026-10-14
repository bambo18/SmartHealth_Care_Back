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
     * 현재 프론트:
     *
     * 전체 보호소
     * radius_km = 1000
     *
     * 지역 선택
     * radius_km = 100
     *
     * 따라서 기존 100km 제한을 1000km까지 허용한다.
     */
    private static final double MAX_RADIUS_KM = 1000.0;

    /*
     * 현재 프론트 전체 보호소 요청:
     *
     * limit = 1000
     */
    private static final int MAX_LIMIT = 1000;


    private final ShelterSearchService shelterSearchService;


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
         * 반경
         */
        if (radiusKm != null && (

                !isFinite(radiusKm)

                || radiusKm <= 0
                || radiusKm > MAX_RADIUS_KM

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