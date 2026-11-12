package com.smarthealthdog.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.walk.EndWalkRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.services.WalkService;

class WalkControllerUT {

    private WalkService walkService;
    private WalkController walkController;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        walkService = mock(WalkService.class);
        walkController = new WalkController(walkService);
        userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("42");
    }

    @Test
    void list_convertsLocalDatesUsingRequestedTimezone() {
        when(walkService.listByPet(
                eq(7L), eq(42L), any(), any(), eq("date_desc"), eq(10), eq(37)))
                .thenReturn(Page.empty());

        walkController.list(
                7L, userDetails, "Asia/Seoul", "2026-10-06", "2026-10-07",
                "date_desc", 10, 37);

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(walkService).listByPet(
                eq(7L), eq(42L), startCaptor.capture(), endCaptor.capture(),
                eq("date_desc"), eq(10), eq(37));

        ZoneId seoul = ZoneId.of("Asia/Seoul");
        assertEquals(
                LocalDate.parse("2026-10-06").atStartOfDay(seoul).toInstant(),
                startCaptor.getValue());
        assertEquals(
                LocalDate.parse("2026-10-08").atStartOfDay(seoul).toInstant(),
                endCaptor.getValue());
    }

    @Test
    void list_rejectsUnknownTimezoneBeforeCallingService() {
        assertThrows(InvalidRequestDataException.class, () -> walkController.list(
                7L, userDetails, "Invalid/Zone", null, null, "date_desc", 10, 0));

        verifyNoInteractions(walkService);
    }

    // ============================================================
    // 산책 종료 - 활동 시간(active_duration_seconds)
    // ============================================================

    /**
     * 종료 요청 본문의 active_duration_seconds가 DTO로 매핑되는지 확인
     */
    @Test
    void endWalkRequest_bindsActiveDurationSecondsFromSnakeCaseJson() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        String json = """
                {
                  "end_time": "2026-10-26T12:20:00Z",
                  "distance": 1.2,
                  "path_coordinates": [],
                  "active_duration_seconds": 600
                }
                """;

        EndWalkRequest request = objectMapper.readValue(json, EndWalkRequest.class);

        assertEquals(Instant.parse("2026-10-26T12:20:00Z"), request.endTime());
        assertEquals(600L, request.activeDurationSeconds());
    }

    /**
     * 활동 시간이 저장된 산책은 종료 응답의 duration으로 활동 시간을 반환
     */
    @Test
    void end_returnsActiveDurationAsDuration() {
        Pet pet = mock(Pet.class);
        when(pet.getId()).thenReturn(7L);

        Walk walk = Walk.builder()
                .pet(pet)
                .startTime(Instant.parse("2026-10-26T12:00:00Z"))
                .endTime(Instant.parse("2026-10-26T12:20:00Z"))
                .distanceKm(new BigDecimal("1.20"))
                .pathCoordinates("[]")
                .activeDurationSeconds(600L)
                .build();

        when(walkService.end(eq(7L), eq(100L), eq(42L), any(EndWalkRequest.class)))
                .thenReturn(walk);

        EndWalkRequest request = new EndWalkRequest(
                Instant.parse("2026-10-26T12:20:00Z"),
                new BigDecimal("1.20"),
                List.of(),
                600L
        );

        var response = walkController.end(7L, 100L, request, userDetails);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody().get("walk");

        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), body.get("start_time"));
        assertEquals(Instant.parse("2026-10-26T12:20:00Z"), body.get("end_time"));
        assertEquals(600L, body.get("duration"));
    }
}