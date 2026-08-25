package com.smarthealthdog.backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.sunlight.LightSampleBatchRequest;
import com.smarthealthdog.backend.dto.sunlight.LightSampleBatchResponse;
import com.smarthealthdog.backend.dto.sunlight.SunlightProgressResponse;
import com.smarthealthdog.backend.services.SunlightService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets/{petId}")
@RequiredArgsConstructor
public class SunlightController {

    private final SunlightService sunlightService;


    // ============================================================
    // 조도 측정값 저장
    // ============================================================

    @PostMapping("/walks/{walkId}/light-samples")
    @PreAuthorize("hasAuthority('can_start_walk')")
    public ResponseEntity<Map<String, Object>> saveSamples(

            @PathVariable
            Long petId,

            @PathVariable
            Long walkId,

            @RequestBody
            @Valid
            LightSampleBatchRequest request,

            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );


        LightSampleBatchResponse result =
                sunlightService.saveSamples(
                        petId,
                        walkId,
                        userId,
                        request
                );


        return ResponseEntity.ok(
                Map.of(
                        "result",
                        result
                )
        );
    }


    // ============================================================
    // 오늘 일광 노출 달성률 조회
    // ============================================================

    @GetMapping("/sunlight/today")
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<Map<String, Object>> getTodayProgress(

            @PathVariable
            Long petId,

            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );


        SunlightProgressResponse result =
                sunlightService.getTodayProgress(
                        petId,
                        userId
                );


        return ResponseEntity.ok(
                Map.of(
                        "sunlight",
                        result
                )
        );
    }
}