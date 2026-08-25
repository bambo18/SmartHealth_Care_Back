package com.smarthealthdog.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.LightSensorSample;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.sunlight.LightSampleBatchRequest;
import com.smarthealthdog.backend.dto.sunlight.LightSampleBatchResponse;
import com.smarthealthdog.backend.dto.sunlight.LightSampleRequest;
import com.smarthealthdog.backend.dto.sunlight.SunlightProgressResponse;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.LightSensorSampleRepository;
import com.smarthealthdog.backend.repositories.WalkRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SunlightService {

    /*
     * 서비스 기준 시간대
     */
    private static final ZoneId SERVICE_ZONE =
            ZoneId.of("Asia/Seoul");

    /*
     * 일광 측정 인정 시간
     *
     * 08:00 이상
     * 17:00 미만
     */
    private static final LocalTime MEASUREMENT_START =
            LocalTime.of(8, 0);

    private static final LocalTime MEASUREMENT_END =
            LocalTime.of(17, 0);

    /*
     * 10분 평균
     */
    private static final long WINDOW_SECONDS =
            10 * 60L;

    /*
     * 30초 간격이면
     *
     * 10분 / 30초 = 20개
     *
     * 현재는 요구사항 그대로
     * 10분 구간에 20개 이상 있어야 계산한다.
     */
    private static final int MIN_SAMPLES_PER_WINDOW =
            20;

    /*
     * 인정 기준
     */
    private static final double QUALIFYING_LUX =
            2_000.0;

    /*
     * 인정된 10분:
     *
     * 2,000 Lux × 10분
     * = 20,000
     */
    private static final int CREDIT_PER_WINDOW =
            20_000;

    /*
     * 일일 목표:
     *
     * 2,000 Lux × 30분
     * = 60,000
     */
    private static final int DAILY_TARGET =
            60_000;


    private final LightSensorSampleRepository lightSensorSampleRepository;
    private final WalkRepository walkRepository;
    private final PetService petService;


    // ============================================================
    // 조도 측정값 저장
    // ============================================================

    @Transactional
    public LightSampleBatchResponse saveSamples(
            Long petId,
            Long walkId,
            Long userId,
            LightSampleBatchRequest request
    ) {

        /*
         * 1. 산책 조회
         */
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                ErrorCode.WALK_NOT_FOUND
                        )
                );


        /*
         * 2. URL petId와 실제 산책 petId 검증
         */
        if (!walk.getPet()
                .getId()
                .equals(petId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.WALK_NOT_FOUND
            );
        }


        /*
         * 3. 소유권 검증
         */
        if (!walk.getPet()
                .getOwner()
                .getId()
                .equals(userId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.WALK_NOT_FOUND
            );
        }


        /*
         * 4. 빈 데이터 검증
         */
        if (request == null
                || request.samples() == null
                || request.samples().isEmpty()) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_LIGHT_SAMPLE
            );
        }


        int receivedCount =
                request.samples().size();

        int savedCount = 0;
        int duplicateCount = 0;


        /*
         * 5. 각 센서 데이터 검증 및 저장
         */
        for (LightSampleRequest sample : request.samples()) {

            if (sample == null
                    || sample.clientSampleId() == null
                    || sample.measuredAt() == null
                    || sample.lux() == null) {

                throw new InvalidRequestDataException(
                        ErrorCode.INVALID_LIGHT_SAMPLE
                );
            }


            /*
             * 음수 / NaN / Infinity 방지
             */
            if (sample.lux() < 0
                    || Double.isNaN(sample.lux())
                    || Double.isInfinite(sample.lux())) {

                throw new InvalidRequestDataException(
                        ErrorCode.INVALID_LIGHT_SAMPLE
                );
            }


            /*
             * 산책 시작 이전의 측정값이면 허용하지 않음
             */
            if (sample.measuredAt()
                    .isBefore(walk.getStartTime())) {

                throw new InvalidRequestDataException(
                        ErrorCode.LIGHT_SAMPLE_OUTSIDE_WALK
                );
            }


            /*
             * 이미 종료된 산책의 경우,
             * 종료 시각 이후 측정값도 허용하지 않음
             */
            if (walk.getEndTime() != null
                    && sample.measuredAt()
                    .isAfter(walk.getEndTime())) {

                throw new InvalidRequestDataException(
                        ErrorCode.LIGHT_SAMPLE_OUTSIDE_WALK
                );
            }


            /*
             * PostgreSQL:
             *
             * INSERT ... ON CONFLICT DO NOTHING
             *
             * 1 → 신규 저장
             * 0 → 중복
             */
            int inserted =
                    lightSensorSampleRepository
                            .insertIgnoreDuplicate(
                                    walkId,
                                    sample.clientSampleId(),
                                    sample.measuredAt(),
                                    sample.lux()
                            );


            if (inserted == 1) {
                savedCount++;
            } else {
                duplicateCount++;
            }
        }


        return new LightSampleBatchResponse(
                receivedCount,
                savedCount,
                duplicateCount
        );
    }


    // ============================================================
    // 오늘의 일광 노출 달성률
    // ============================================================

    @Transactional(readOnly = true)
    public SunlightProgressResponse getTodayProgress(
            Long petId,
            Long userId
    ) {

        /*
         * 1. 반려동물 + 소유권 확인
         */
        Pet pet = petService.get(petId);

        if (!pet.getOwner()
                .getId()
                .equals(userId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }


        /*
         * 2. 한국 시간 기준 오늘
         */
        LocalDate today =
                LocalDate.now(SERVICE_ZONE);


        ZonedDateTime startZoned =
                today.atTime(MEASUREMENT_START)
                        .atZone(SERVICE_ZONE);


        ZonedDateTime endZoned =
                today.atTime(MEASUREMENT_END)
                        .atZone(SERVICE_ZONE);


        Instant dayStart =
                startZoned.toInstant();

        Instant dayEnd =
                endZoned.toInstant();


        /*
         * 현재 시간이 17:00 이전이면 현재 시각까지만 계산
         *
         * 17:00 이후라면 17:00까지만 계산
         */
        Instant now =
                Instant.now();


        Instant effectiveEnd =
                now.isBefore(dayEnd)
                        ? now
                        : dayEnd;


        /*
         * 아직 오전 8시 이전이라면
         * 계산할 데이터 없음
         */
        if (!effectiveEnd.isAfter(dayStart)) {

            return createProgressResponse(
                    today,
                    0,
                    0,
                    0
            );
        }


        /*
         * 3. 오늘 08:00 ~ 현재 또는 17:00까지 조도 조회
         */
        List<LightSensorSample> samples =
                lightSensorSampleRepository
                        .findByPetAndMeasuredAtBetween(
                                petId,
                                dayStart,
                                effectiveEnd
                        );


        if (samples.isEmpty()) {

            return createProgressResponse(
                    today,
                    0,
                    0,
                    0
            );
        }


        /*
         * 4. 산책별로 분리
         *
         * 서로 다른 산책의 데이터를
         * 하나의 10분 평균에 섞지 않는다.
         */
        Map<Long, List<LightSensorSample>> samplesByWalk =
                samples.stream()
                        .collect(
                                Collectors.groupingBy(
                                        sample ->
                                                sample.getWalk()
                                                        .getId(),

                                        LinkedHashMap::new,

                                        Collectors.toList()
                                )
                        );


        int qualifiedWindows = 0;


        /*
         * 5. 각 산책을 10분 단위로 계산
         */
        for (List<LightSensorSample> walkSamples
                : samplesByWalk.values()) {

            if (walkSamples.isEmpty()) {
                continue;
            }


            Walk walk =
                    walkSamples.get(0)
                            .getWalk();


            /*
             * 계산 시작 시각
             *
             * 산책 시작이 08:00 이전이면
             * 08:00부터 시작
             */
            Instant calculationStart =
                    walk.getStartTime()
                            .isAfter(dayStart)

                            ? walk.getStartTime()
                            : dayStart;


            /*
             * 계산 종료 시각
             */
            Instant calculationEnd =
                    effectiveEnd;


            /*
             * 산책이 이미 종료됐다면
             * 실제 산책 종료시각까지만 계산
             */
            if (walk.getEndTime() != null
                    && walk.getEndTime()
                    .isBefore(calculationEnd)) {

                calculationEnd =
                        walk.getEndTime();
            }


            if (!calculationEnd
                    .isAfter(calculationStart)) {

                continue;
            }


            /*
             * 완전히 끝난 10분 구간 개수
             *
             * 9분 59초 → 0
             * 10분     → 1
             * 20분     → 2
             */
            long completeWindows =
                    Duration.between(
                            calculationStart,
                            calculationEnd
                    ).getSeconds()
                            / WINDOW_SECONDS;


            /*
             * 각각의 10분 구간 평가
             */
            for (int windowIndex = 0;
                 windowIndex < completeWindows;
                 windowIndex++) {


                Instant windowStart =
                        calculationStart.plusSeconds(
                                windowIndex
                                        * WINDOW_SECONDS
                        );


                Instant windowEnd =
                        windowStart.plusSeconds(
                                WINDOW_SECONDS
                        );


                List<LightSensorSample> windowSamples =
                        walkSamples.stream()
                                .filter(sample ->
                                        !sample.getMeasuredAt()
                                                .isBefore(
                                                        windowStart
                                                )
                                )
                                .filter(sample ->
                                        sample.getMeasuredAt()
                                                .isBefore(
                                                        windowEnd
                                                )
                                )
                                .toList();


                /*
                 * 30초마다 측정하면
                 * 10분에 20개
                 *
                 * 현재 요구사항에서는
                 * 20개가 모여야 해당 10분을 인정
                 */
                if (windowSamples.size()
                        < MIN_SAMPLES_PER_WINDOW) {

                    continue;
                }


                /*
                 * 10분 평균 조도 계산
                 */
                double averageLux =
                        windowSamples.stream()
                                .mapToDouble(
                                        LightSensorSample::getLux
                                )
                                .average()
                                .orElse(0.0);


                /*
                 * 평균이 2,000 Lux 이상이면
                 *
                 * 실제 평균이
                 *
                 * 2,500이든
                 * 8,000이든
                 * 50,000이든
                 *
                 * 계산에서는 동일하게
                 * 2,000 × 10분 = 20,000 인정
                 */
                if (averageLux >= QUALIFYING_LUX) {

                    qualifiedWindows++;
                }
            }
        }


        /*
         * 실제 인정시간
         */
        int qualifyingMinutes =
                qualifiedWindows * 10;


        /*
         * 인정 노출량
         *
         * 하루 목표 이상이어도
         * 최대 60,000으로 표시
         */
        int achievedLuxMinutes =
                Math.min(
                        DAILY_TARGET,
                        qualifiedWindows
                                * CREDIT_PER_WINDOW
                );


        return createProgressResponse(
                today,
                achievedLuxMinutes,
                qualifyingMinutes,
                qualifiedWindows,
                samples.size()
        );
    }


    // ============================================================
    // 응답 생성
    // ============================================================

    private SunlightProgressResponse createProgressResponse(
            LocalDate date,
            int achievedLuxMinutes,
            int qualifyingMinutes,
            int qualifiedWindows
    ) {

        return createProgressResponse(
                date,
                achievedLuxMinutes,
                qualifyingMinutes,
                qualifiedWindows,
                0
        );
    }


    private SunlightProgressResponse createProgressResponse(
            LocalDate date,
            int achievedLuxMinutes,
            int qualifyingMinutes,
            int qualifiedWindows,
            int sampleCount
    ) {

        double progressPercent =
                DAILY_TARGET == 0
                        ? 0.0
                        : achievedLuxMinutes
                        * 100.0
                        / DAILY_TARGET;


        progressPercent =
                Math.min(
                        100.0,
                        progressPercent
                );


        /*
         * 소수점 둘째 자리까지
         */
        progressPercent =
                Math.round(
                        progressPercent
                                * 100.0
                ) / 100.0;


        boolean completed =
                achievedLuxMinutes
                        >= DAILY_TARGET;


        return new SunlightProgressResponse(
                date,
                DAILY_TARGET,
                achievedLuxMinutes,
                qualifyingMinutes,
                qualifiedWindows,
                sampleCount,
                progressPercent,
                completed
        );
    }
}