package com.smarthealthdog.backend.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.smarthealthdog.backend.dto.sunlight.SunlightProgressResponse;
import com.smarthealthdog.backend.handlers.GlobalExceptionHandler;
import com.smarthealthdog.backend.services.SunlightService;
import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * SunlightController 단위 테스트
 *
 * GET /api/pets/{petId}/sunlight/today 의
 * 선택적 date 쿼리 파라미터 바인딩을 검증한다.
 *
 * 서비스는 목으로 대체하고,
 * 실제 쿼리 파라미터 파싱 경로를 거치도록
 * standalone MockMvc를 사용한다.
 */
class SunlightControllerUT {

    private static final Long PET_ID = 7L;
    private static final Long USER_ID = 42L;

    private SunlightService sunlightService;
    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {

        sunlightService =
                mock(SunlightService.class);


        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new SunlightController(
                                sunlightService
                        )
                )
                /*
                 * @AuthenticationPrincipal 주입
                 */
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                /*
                 * 날짜 형식 오류 응답 검증용
                 */
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();


        /*
         * UserDetails의 username은 내부 숫자 user.id
         */
        UserDetails userDetails =
                mock(UserDetails.class);

        when(userDetails.getUsername())
                .thenReturn(
                        String.valueOf(USER_ID)
                );


        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                List.of()
                        )
                );
    }


    @AfterEach
    void tearDown() {

        SecurityContextHolder.clearContext();
    }


    private SunlightProgressResponse progressOf(
            LocalDate date
    ) {

        return new SunlightProgressResponse(
                date,
                60_000,
                40_000,
                20,
                2,
                120,
                66.67,
                false
        );
    }


    // ============================================================
    // date 생략 - 기존 동작
    // ============================================================

    @Test
    @DisplayName("date를 생략하면 서비스에 null을 넘겨 오늘 기준으로 조회한다")
    void today_withoutDate_passesNull() throws Exception {

        LocalDate today =
                LocalDate.now();

        when(sunlightService.getProgressByDate(
                eq(PET_ID),
                eq(USER_ID),
                eq(null)
        )).thenReturn(
                progressOf(today)
        );


        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                PET_ID
                        )
                )
                .andExpect(
                        status().isOk()
                )
                /*
                 * 응답 JSON의 date 문자열 형식은
                 * SunlightControllerTest에서 검증한다.
                 */
                .andExpect(
                        jsonPath("$.sunlight.target_lux_minutes")
                                .value(60_000)
                )
                .andExpect(
                        jsonPath("$.sunlight.achieved_lux_minutes")
                                .value(40_000)
                );


        verify(sunlightService)
                .getProgressByDate(
                        PET_ID,
                        USER_ID,
                        null
                );
    }


    // ============================================================
    // date 전달 - 특정 날짜 누적
    // ============================================================

    @Test
    @DisplayName("date를 전달하면 해당 날짜로 파싱해 서비스에 전달한다")
    void today_withDate_passesParsedDate() throws Exception {

        LocalDate requested =
                LocalDate.of(2026, 9, 25);

        when(sunlightService.getProgressByDate(
                eq(PET_ID),
                eq(USER_ID),
                eq(requested)
        )).thenReturn(
                progressOf(requested)
        );


        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                PET_ID
                        )
                                .param(
                                        "date",
                                        "2026-09-25"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.sunlight.qualifying_minutes")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.sunlight.progress_percent")
                                .value(66.67)
                );


        verify(sunlightService)
                .getProgressByDate(
                        PET_ID,
                        USER_ID,
                        requested
                );
    }


    // ============================================================
    // 잘못된 날짜 형식
    // ============================================================

    @Test
    @DisplayName("date 형식이 잘못되면 INVALID_PARAMETER_TYPE으로 400을 응답한다")
    void today_withInvalidDate_returnsBadRequest() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/pets/{petId}/sunlight/today",
                                PET_ID
                        )
                                .param(
                                        "date",
                                        "2026-9-5"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code[0]")
                                .value(
                                        ErrorCode.INVALID_PARAMETER_TYPE
                                                .name()
                                )
                );


        verifyNoInteractions(sunlightService);
    }


    // ============================================================
    // 제거된 중복 엔드포인트
    // ============================================================

    @Test
    @DisplayName("중복이었던 GET /sunlight 엔드포인트는 더 이상 매핑되지 않는다")
    void removedSunlightEndpoint_isNotMapped() throws Exception {

        /*
         * 매핑 자체가 없는지만 확인한다.
         *
         * GlobalExceptionHandler를 붙이면
         * standalone MockMvc에서는 NoHandlerFoundException이
         * 공통 핸들러에 걸려 500으로 바뀌므로
         * 이 테스트에서는 advice 없이 라우팅만 본다.
         *
         * 실제 서비스에서는 정적 리소스 처리로 넘어가
         * NoResourceFoundException → 404로 응답한다.
         */
        MockMvc routingOnly = MockMvcBuilders
                .standaloneSetup(
                        new SunlightController(
                                sunlightService
                        )
                )
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();


        routingOnly.perform(
                        get(
                                "/api/pets/{petId}/sunlight",
                                PET_ID
                        )
                                .param(
                                        "date",
                                        "2026-09-25"
                                )
                )
                .andExpect(
                        status().isNotFound()
                );


        verifyNoInteractions(sunlightService);
    }
}
