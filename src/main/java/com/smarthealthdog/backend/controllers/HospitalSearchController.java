package com.smarthealthdog.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.hospital.HospitalSearchResponse;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.services.HospitalSearchService;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
public class HospitalSearchController {

    private final HospitalSearchService hospitalSearchService;

    // [GET] /api/hospitals/search
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('can_view_hospitals')")
    public ResponseEntity<HospitalSearchResponse> search(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(name = "radius_km", required = false) Double radiusKm,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {

        // 공통 예외
        if ((location == null || location.isBlank()) && (lat == null || lng == null)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        HospitalSearchResponse resp = hospitalSearchService.search(
                location,
                lat,
                lng,
                radiusKm,
                sortBy,
                limit,
                offset
        );
        return ResponseEntity.ok(resp);
    }
}