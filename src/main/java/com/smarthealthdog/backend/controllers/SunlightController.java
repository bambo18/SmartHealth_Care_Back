package com.smarthealthdog.backend.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    // 일광 노출 달성률 조회 (오늘 / 특정 날짜)
    // ============================================================

    /*
     * GET /api/pets/{petId}/sunlight/today
     * GET /api/pets/{petId}/sunlight/today?date=YYYY-MM-DD
     *
     * date를 생략하면 기존과 동일하게 오늘(Asia/Seoul) 기준으로 조회하고,
     * date를 전달하면 해당 날짜 하루의 누적 결과를 반환한다.
     *
     * 요청의 date는 "이 날짜 결과를 주세요"라는 입력이고,
     * 응답의 date는 "이 결과는 그 날짜의 결과입니다"라는 출력이므로
     * 응답의 date 필드는 그대로 유지한다.
     *
     * 날짜 형식이 잘못된 경우
     * MethodArgumentTypeMismatchException이 발생하며
     * GlobalExceptionHandler가 INVALID_PARAMETER_TYPE으로 응답한다.
     */
    @GetMapping("/sunlight/today")
    @PreAuthorize("hasAuthority('can_view_own_walk_records')")
    public ResponseEntity<Map<String, Object>> getTodayProgress(

            @PathVariable
            Long petId,

            /*
             * 조회할 날짜 (Asia/Seoul 기준)
             *
             * 생략하면 오늘
             */
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @AuthenticationPrincipal
            UserDetails userDetails
    ) {

        Long userId =
                Long.parseLong(
                        userDetails.getUsername()
                );


        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        petId,
                        userId,
                        date
                );


        return ResponseEntity.ok(
                Map.of(
                        "sunlight",
                        result
                )
        );
    }
}