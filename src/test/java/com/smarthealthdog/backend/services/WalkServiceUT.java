package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.walk.EndWalkRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.WalkRepository;
import com.smarthealthdog.backend.utils.DateUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

class WalkServiceUT {

    private WalkRepository walkRepository;
    private PetService petService;
    private WalkService walkService;

    @BeforeEach
    void setUp() {
        walkRepository = mock(WalkRepository.class);
        petService = mock(PetService.class);
        walkService = new WalkService(walkRepository, petService, mock(DateUtils.class));
    }

    @Test
    void listByPet_passesRawOffsetToDatabasePageable() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(42L);
        Pet pet = mock(Pet.class);
        when(pet.getOwner()).thenReturn(owner);
        when(petService.get(7L)).thenReturn(pet);
        when(walkRepository.findByPetId(eq(7L), any(Pageable.class)))
                .thenReturn(Page.empty());

        walkService.listByPet(7L, 42L, null, null, "date_asc", 10, 37);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(walkRepository).findByPetId(eq(7L), pageableCaptor.capture());
        assertEquals(37L, pageableCaptor.getValue().getOffset());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
        assertEquals(Sort.Direction.ASC, pageableCaptor.getValue().getSort().getOrderFor("startTime").getDirection());
    }

    // ============================================================
    // 산책 종료 - 활동 시간(active_duration_seconds)
    // ============================================================

    private static final Instant START = Instant.parse("2026-10-26T12:00:00Z");
    private static final Instant END = Instant.parse("2026-10-26T12:20:00Z");

    /**
     * 12:00 시작 ~ 12:20 종료(전체 경과 1200초)인 진행 중 산책을 준비한다.
     */
    private Walk prepareOngoingWalk() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(42L);

        Pet pet = mock(Pet.class);
        when(pet.getId()).thenReturn(7L);
        when(pet.getOwner()).thenReturn(owner);

        Walk walk = Walk.builder()
                .pet(pet)
                .startTime(START)
                .endTime(null)
                .distanceKm(BigDecimal.ZERO)
                .pathCoordinates("[]")
                .build();

        when(walkRepository.findById(100L)).thenReturn(Optional.of(walk));
        when(walkRepository.save(any(Walk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        return walk;
    }

    private EndWalkRequest endRequest(Long activeDurationSeconds) {
        return new EndWalkRequest(
                END,
                new BigDecimal("1.20"),
                List.of(),
                activeDurationSeconds
        );
    }

    @Test
    void end_usesActiveDurationAsWalkDuration() {
        prepareOngoingWalk();

        Walk result = walkService.end(7L, 100L, 42L, endRequest(600L));

        // 시작/종료 시각은 실제 시각 유지
        assertEquals(START, result.getStartTime());
        assertEquals(END, result.getEndTime());

        // 산책 시간은 휴식을 제외한 활동 시간
        assertEquals(600L, result.getDurationSeconds());
        assertEquals(600L, result.getActiveDurationSeconds());

        // 전체 경과 시간은 그대로 1200초
        assertEquals(1200L, result.getElapsedSeconds());
    }

    @Test
    void end_withoutActiveDuration_keepsElapsedTimeAsDuration() {
        prepareOngoingWalk();

        Walk result = walkService.end(7L, 100L, 42L, endRequest(null));

        assertNull(result.getActiveDurationSeconds());
        assertEquals(1200L, result.getDurationSeconds());
    }

    @Test
    void end_acceptsActiveDurationEqualToElapsedTime() {
        prepareOngoingWalk();

        Walk result = walkService.end(7L, 100L, 42L, endRequest(1200L));

        assertEquals(1200L, result.getDurationSeconds());
    }

    @Test
    void end_rejectsNegativeActiveDuration() {
        prepareOngoingWalk();

        InvalidRequestDataException exception = assertThrows(
                InvalidRequestDataException.class,
                () -> walkService.end(7L, 100L, 42L, endRequest(-1L))
        );

        assertEquals(ErrorCode.INVALID_WALK_ACTIVE_DURATION, exception.getErrorCode());
        verify(walkRepository, never()).save(any(Walk.class));
    }

    @Test
    void end_rejectsActiveDurationExceedingElapsedTime() {
        prepareOngoingWalk();

        InvalidRequestDataException exception = assertThrows(
                InvalidRequestDataException.class,
                () -> walkService.end(7L, 100L, 42L, endRequest(1800L))
        );

        assertEquals(ErrorCode.INVALID_WALK_ACTIVE_DURATION, exception.getErrorCode());
        verify(walkRepository, never()).save(any(Walk.class));
    }
}