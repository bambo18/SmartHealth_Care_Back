package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.LightSensorSample;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.sunlight.SunlightProgressResponse;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.LightSensorSampleRepository;
import com.smarthealthdog.backend.repositories.WalkRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * SunlightService 단위 테스트
 *
 * 특정 날짜 조회(getProgressByDate)와
 * 기존 오늘 조회(getTodayProgress)의 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SunlightServiceUT {

    private static final ZoneId ZONE =
            ZoneId.of("Asia/Seoul");

    private static final Long PET_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock
    private LightSensorSampleRepository lightSensorSampleRepository;

    @Mock
    private WalkRepository walkRepository;

    @Mock
    private PetService petService;

    @InjectMocks
    private SunlightService sunlightService;

    @Captor
    private ArgumentCaptor<Instant> startCaptor;

    @Captor
    private ArgumentCaptor<Instant> endCaptor;

    private Pet pet;


    @BeforeEach
    void setUp() {

        User owner =
                User.builder()
                        .id(OWNER_ID)
                        .build();

        pet = Pet.builder()
                .owner(owner)
                .name("테스트견")
                .build();

        pet.setId(PET_ID);
    }


    // ============================================================
    // 테스트 픽스처 헬퍼
    // ============================================================

    /**
     * 지정한 시각/종료시각을 갖는 산책 생성
     */
    private Walk walk(
            Long walkId,
            Instant startTime,
            Instant endTime
    ) {

        Walk walk =
                Walk.builder()
                        .pet(pet)
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();

        /*
         * walk.id는 DB가 생성하는 값이라 세터가 없다.
         * 서비스가 산책별 그룹핑에 id를 사용하므로 직접 주입한다.
         */
        ReflectionTestUtils.setField(walk, "id", walkId);

        return walk;
    }


    /**
     * 30초 간격 조도 샘플 생성
     */
    private List<LightSensorSample> samples(
            Walk walk,
            Instant firstMeasuredAt,
            int count,
            double lux
    ) {

        List<LightSensorSample> list =
                new ArrayList<>();

        for (int i = 0; i < count; i++) {

            list.add(
                    LightSensorSample.builder()
                            .walk(walk)
                            .clientSampleId(UUID.randomUUID())
                            .measuredAt(
                                    firstMeasuredAt.plusSeconds(i * 30L)
                            )
                            .lux(lux)
                            .build()
            );
        }

        return list;
    }


    private Instant atKst(
            LocalDate date,
            int hour,
            int minute
    ) {

        return date.atStartOfDay(ZONE)
                .plusHours(hour)
                .plusMinutes(minute)
                .toInstant();
    }


    // ============================================================
    // 특정 날짜 조회
    // ============================================================

    @Test
    @DisplayName("과거 날짜 - 30분(10분×3구간) 모두 2,000 Lux 이상이면 일일 목표 달성")
    void getProgressByDate_pastDate_allWindowsQualified() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .minusDays(3);

        Instant walkStart = atKst(target, 10, 0);
        Instant walkEnd = atKst(target, 10, 30);

        Walk walk =
                walk(100L, walkStart, walkEnd);

        /*
         * 30초 × 60개 = 30분
         * 10분 구간마다 20개씩 채워진다.
         */
        when(petService.get(PET_ID))
                .thenReturn(pet);

        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .thenReturn(
                        samples(walk, walkStart, 60, 3_000.0)
                );


        // ACT
        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        PET_ID,
                        OWNER_ID,
                        target
                );


        // ASSERT
        assertEquals(target, result.date());
        assertEquals(3, result.qualifiedWindows());
        assertEquals(30, result.qualifyingMinutes());
        assertEquals(60_000, result.achievedLuxMinutes());
        assertEquals(60, result.sampleCount());
        assertEquals(100.0, result.progressPercent());
        assertTrue(result.completed());
    }


    @Test
    @DisplayName("과거 날짜 - 조회 범위가 그 날 00:00 ~ 24:00 으로 고정된다")
    void getProgressByDate_pastDate_usesFullDayRange() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .minusDays(3);

        when(petService.get(PET_ID))
                .thenReturn(pet);

        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        startCaptor.capture(),
                        endCaptor.capture()
                ))
                .thenReturn(List.of());


        // ACT
        sunlightService.getProgressByDate(
                PET_ID,
                OWNER_ID,
                target
        );


        // ASSERT
        assertEquals(
                target.atStartOfDay(ZONE).toInstant(),
                startCaptor.getValue()
        );

        assertEquals(
                target.plusDays(1)
                        .atStartOfDay(ZONE)
                        .toInstant(),
                endCaptor.getValue()
        );
    }


    @Test
    @DisplayName("과거 날짜 - 조도 데이터가 없으면 오류가 아니라 달성률 0")
    void getProgressByDate_noSamples_returnsZeroProgress() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .minusDays(30);

        when(petService.get(PET_ID))
                .thenReturn(pet);

        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .thenReturn(List.of());


        // ACT
        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        PET_ID,
                        OWNER_ID,
                        target
                );


        // ASSERT
        assertEquals(target, result.date());
        assertEquals(0, result.achievedLuxMinutes());
        assertEquals(0, result.qualifyingMinutes());
        assertEquals(0, result.qualifiedWindows());
        assertEquals(0, result.sampleCount());
        assertEquals(0.0, result.progressPercent());
        assertFalse(result.completed());
    }


    @Test
    @DisplayName("미래 날짜 - 조회 없이 달성률 0으로 응답한다")
    void getProgressByDate_futureDate_skipsQuery() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .plusDays(1);

        when(petService.get(PET_ID))
                .thenReturn(pet);


        // ACT
        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        PET_ID,
                        OWNER_ID,
                        target
                );


        // ASSERT
        assertEquals(target, result.date());
        assertEquals(0, result.achievedLuxMinutes());
        assertFalse(result.completed());

        verifyNoInteractions(lightSensorSampleRepository);
    }


    @Test
    @DisplayName("10분 구간 평균이 2,000 Lux 미만이면 인정되지 않는다")
    void getProgressByDate_belowQualifyingLux_notCounted() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .minusDays(2);

        Instant walkStart = atKst(target, 14, 0);
        Instant walkEnd = atKst(target, 14, 30);

        Walk walk =
                walk(200L, walkStart, walkEnd);

        when(petService.get(PET_ID))
                .thenReturn(pet);

        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .thenReturn(
                        samples(walk, walkStart, 60, 1_000.0)
                );


        // ACT
        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        PET_ID,
                        OWNER_ID,
                        target
                );


        // ASSERT
        assertEquals(0, result.qualifiedWindows());
        assertEquals(0, result.achievedLuxMinutes());
        assertEquals(60, result.sampleCount());
        assertFalse(result.completed());
    }


    @Test
    @DisplayName("10분 구간 샘플이 20개 미만이면 인정되지 않는다")
    void getProgressByDate_notEnoughSamples_notCounted() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .minusDays(2);

        Instant walkStart = atKst(target, 9, 0);
        Instant walkEnd = atKst(target, 9, 10);

        Walk walk =
                walk(300L, walkStart, walkEnd);

        when(petService.get(PET_ID))
                .thenReturn(pet);

        /*
         * 10분 구간에 19개만 존재
         */
        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .thenReturn(
                        samples(walk, walkStart, 19, 5_000.0)
                );


        // ACT
        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        PET_ID,
                        OWNER_ID,
                        target
                );


        // ASSERT
        assertEquals(0, result.qualifiedWindows());
        assertEquals(19, result.sampleCount());
    }


    @Test
    @DisplayName("자정을 넘긴 산책은 조회 날짜 00:00 부터만 계산한다")
    void getProgressByDate_walkSpanningMidnight_clampsToDayStart() {

        // ARRANGE
        LocalDate target =
                LocalDate.now(ZONE)
                        .minusDays(1);

        /*
         * 전날 23:50 시작 → 조회 날짜 00:30 종료
         *
         * 조회 날짜 기준으로는
         * 00:00 ~ 00:30 의 30분만 계산 대상이다.
         */
        Instant walkStart =
                atKst(target.minusDays(1), 23, 50);

        Instant walkEnd =
                atKst(target, 0, 30);

        Walk walk =
                walk(400L, walkStart, walkEnd);

        Instant dayStart =
                target.atStartOfDay(ZONE)
                        .toInstant();

        when(petService.get(PET_ID))
                .thenReturn(pet);

        /*
         * 리포지토리는 조회 날짜 범위의 샘플만 반환한다.
         * (00:00 ~ 00:30, 30초 간격 60개)
         */
        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        any(Instant.class),
                        any(Instant.class)
                ))
                .thenReturn(
                        samples(walk, dayStart, 60, 4_000.0)
                );


        // ACT
        SunlightProgressResponse result =
                sunlightService.getProgressByDate(
                        PET_ID,
                        OWNER_ID,
                        target
                );


        // ASSERT
        assertEquals(target, result.date());
        assertEquals(3, result.qualifiedWindows());
        assertEquals(30, result.qualifyingMinutes());
        assertTrue(result.completed());
    }


    @Test
    @DisplayName("다른 사용자의 반려동물은 조회할 수 없다")
    void getProgressByDate_notOwner_throwsResourceNotFound() {

        // ARRANGE
        when(petService.get(PET_ID))
                .thenReturn(pet);


        // ACT & ASSERT
        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> sunlightService.getProgressByDate(
                                PET_ID,
                                OTHER_USER_ID,
                                LocalDate.now(ZONE)
                        )
                );

        assertEquals(
                ErrorCode.RESOURCE_NOT_FOUND,
                exception.getErrorCode()
        );

        verifyNoInteractions(lightSensorSampleRepository);
    }


    // ============================================================
    // 오늘 조회 (기존 엔드포인트 하위호환)
    // ============================================================

    @Test
    @DisplayName("getTodayProgress는 오늘 00:00 ~ 현재 시각 범위로 조회한다")
    void getTodayProgress_usesTodayUntilNow() {

        // ARRANGE
        LocalDate today =
                LocalDate.now(ZONE);

        Instant beforeCall = Instant.now();

        when(petService.get(PET_ID))
                .thenReturn(pet);

        when(lightSensorSampleRepository
                .findByPetAndMeasuredAtBetween(
                        eq(PET_ID),
                        startCaptor.capture(),
                        endCaptor.capture()
                ))
                .thenReturn(List.of());


        // ACT
        SunlightProgressResponse result =
                sunlightService.getTodayProgress(
                        PET_ID,
                        OWNER_ID
                );

        Instant afterCall = Instant.now();


        // ASSERT
        assertEquals(today, result.date());

        assertEquals(
                today.atStartOfDay(ZONE).toInstant(),
                startCaptor.getValue()
        );

        /*
         * 종료 시각은 "현재 시각"이어야 한다.
         */
        Instant capturedEnd = endCaptor.getValue();

        assertFalse(capturedEnd.isBefore(beforeCall));
        assertFalse(capturedEnd.isAfter(afterCall));
    }
}
