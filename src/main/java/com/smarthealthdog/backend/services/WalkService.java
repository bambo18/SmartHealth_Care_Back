package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.walk.CreateWalkRequest;
import com.smarthealthdog.backend.dto.walk.EndWalkRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.WalkRepository;
import com.smarthealthdog.backend.utils.DateUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class WalkService {

    private final WalkRepository walkRepository;
    private final PetService petService;
    private final DateUtils dateUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();


    // ============================================================
    // 산책 시작
    // ============================================================

    @Transactional
    public Walk create(
            Long petId,
            Long userId,
            CreateWalkRequest req
    ) {

        // 1. 반려동물 조회
        Pet pet = petService.get(petId);

        // 2. 해당 사용자의 반려동물인지 확인
        if (!pet.getOwner().getId().equals(userId)) {

            /*
             * 다른 사용자의 반려동물 존재 여부를 노출하지 않기 위해
             * 기존 프로젝트 방식과 동일하게 404 처리
             */
            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        /*
         * 3. 산책 시작 데이터 생성
         *
         * 산책 시작 시에는:
         *
         * startTime       = 앱에서 전달
         * endTime         = null
         * distanceKm      = 0
         * pathCoordinates = []
         */
        Walk walk = Walk.builder()
                .pet(pet)
                .startTime(req.startTime())
                .endTime(null)
                .distanceKm(BigDecimal.ZERO)
                .pathCoordinates("[]")
                .build();

        // 4. DB 저장
        return walkRepository.save(walk);
    }


    // ============================================================
    // 산책 단건 조회
    // ============================================================

    @Transactional(readOnly = true)
    public Walk get(
            Long walkId,
            Long userId
    ) {

        Walk walk =
                walkRepository.findById(walkId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        ErrorCode.WALK_NOT_FOUND
                                )
                        );

        // 소유권 검증
        if (!walk.getPet()
                .getOwner()
                .getId()
                .equals(userId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.WALK_NOT_FOUND
            );
        }

        return walk;
    }


    // ============================================================
    // 특정 반려동물 산책 목록
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Walk> listByPet(
            Long petId,
            Long userId,
            Instant startInclusive,
            Instant endExclusive,
            String sortBy,
            Integer limit,
            Integer offset
    ) {

        // --------------------------------------------------------
        // 1. 반려동물 조회
        // --------------------------------------------------------

        Pet pet =
                petService.get(petId);


        // --------------------------------------------------------
        // 2. 소유권 검증
        // --------------------------------------------------------

        if (!pet.getOwner()
                .getId()
                .equals(userId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }


        // --------------------------------------------------------
        // 3. 페이징 값 검증
        // --------------------------------------------------------

        if (limit == null
                || limit <= 0
                || limit > 50
                || offset == null
                || offset < 0) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_INPUT
            );
        }


        // --------------------------------------------------------
        // 4. 정렬
        // --------------------------------------------------------

        Sort.Direction direction =
                "date_asc".equals(sortBy)
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;


        Pageable pageable =
                new OffsetBasedPageable(
                        offset,
                        limit,
                        Sort.by(
                                direction,
                                "startTime"
                        )
                );


        // --------------------------------------------------------
        // 5. 날짜 조건 없음
        // --------------------------------------------------------

        if (startInclusive == null
                && endExclusive == null) {

            return walkRepository.findByPetId(
                    petId,
                    pageable
            );
        }


        // --------------------------------------------------------
        // 6. 시작 날짜만 있음
        // --------------------------------------------------------

        if (startInclusive != null
                && endExclusive == null) {

            return walkRepository
                    .findByPetIdAndStartTimeGreaterThanEqual(
                            petId,
                            startInclusive,
                            pageable
                    );
        }


        // --------------------------------------------------------
        // 7. 종료 날짜만 있음
        // --------------------------------------------------------

        if (startInclusive == null) {

            return walkRepository
                    .findByPetIdAndStartTimeLessThan(
                            petId,
                            endExclusive,
                            pageable
                    );
        }


        // --------------------------------------------------------
        // 8. 시작 날짜 + 종료 날짜 모두 있음
        // --------------------------------------------------------

        return walkRepository
                .findByPetIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                        petId,
                        startInclusive,
                        endExclusive,
                        pageable
                );
    }


    // ============================================================
    // 산책 삭제
    // ============================================================

    @Transactional
    public void delete(
            Long walkId,
            Long userId
    ) {

        Walk walk =
                walkRepository.findById(walkId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        ErrorCode.WALK_NOT_FOUND
                                )
                        );

        // 소유권 검증
        if (!walk.getPet()
                .getOwner()
                .getId()
                .equals(userId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.WALK_NOT_FOUND
            );
        }

        walkRepository.deleteById(walkId);
    }


    // ============================================================
    // 산책 종료
    // ============================================================

    @Transactional
    public Walk end(
            Long petId,
            Long walkId,
            Long userId,
            EndWalkRequest req
    ) {

        // --------------------------------------------------------
        // 1. 산책 조회
        // --------------------------------------------------------

        Walk walk =
                walkRepository.findById(walkId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        ErrorCode.WALK_NOT_FOUND
                                )
                        );


        // --------------------------------------------------------
        // 2. URL petId와 실제 산책의 petId 비교
        // --------------------------------------------------------

        if (!walk.getPet()
                .getId()
                .equals(petId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.WALK_NOT_FOUND
            );
        }


        // --------------------------------------------------------
        // 3. 소유권 검증
        // --------------------------------------------------------

        if (!walk.getPet()
                .getOwner()
                .getId()
                .equals(userId)) {

            throw new ResourceNotFoundException(
                    ErrorCode.WALK_NOT_FOUND
            );
        }


        // --------------------------------------------------------
        // 4. 이미 종료된 산책
        // --------------------------------------------------------
        //
        // 기존 동작을 유지한다.
        //
        // 동일 종료 요청이 다시 들어오더라도
        // 이미 종료된 산책이면 기존 결과 반환
        // --------------------------------------------------------

        if (walk.getEndTime() != null) {
            return walk;
        }


        // --------------------------------------------------------
        // 5. 종료 시간 검증
        // --------------------------------------------------------

        if (req.endTime()
                .isBefore(
                        walk.getStartTime()
                )) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_WALK_TIME_RANGE
            );
        }


        // --------------------------------------------------------
        // 6. 거리 검증
        // --------------------------------------------------------

        if (req.distanceKm()
                .compareTo(
                        BigDecimal.ZERO
                ) < 0) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_WALK_DISTANCE
            );
        }


        // --------------------------------------------------------
        // 7. 활동 시간 검증
        // --------------------------------------------------------
        //
        // 활동 시간은 일시정지(휴식)를 제외한 실제 산책 시간이다.
        //
        // 선택 필드이므로 null이면 검증을 건너뛰고 저장하지 않는다.
        // 이 경우 산책 시간은 기존 방식(end_time - start_time)으로 계산된다.
        //
        // 값이 있으면
        // - 0 이상
        // - 전체 경과 시간 이하
        //
        // 여야 한다.
        // --------------------------------------------------------

        Long activeDurationSeconds =
                req.activeDurationSeconds();

        if (activeDurationSeconds != null) {

            long elapsedSeconds =
                    Duration.between(
                            walk.getStartTime(),
                            req.endTime()
                    ).getSeconds();

            if (activeDurationSeconds < 0
                    || activeDurationSeconds > elapsedSeconds) {

                throw new InvalidRequestDataException(
                        ErrorCode.INVALID_WALK_ACTIVE_DURATION
                );
            }
        }


        // --------------------------------------------------------
        // 8. 좌표 JSON 직렬화
        // --------------------------------------------------------

        String pathJson = "[]";

        try {

            if (req.pathCoordinates() != null) {

                pathJson =
                        objectMapper.writeValueAsString(
                                req.pathCoordinates()
                        );
            }

        } catch (JsonProcessingException e) {

            throw new InvalidRequestDataException(
                    ErrorCode.INVALID_WALK_PATH
            );
        }


        // --------------------------------------------------------
        // 9. 산책 종료
        // --------------------------------------------------------

        walk.complete(
                req.endTime(),
                req.distanceKm(),
                pathJson,
                activeDurationSeconds
        );


        // --------------------------------------------------------
        // 10. 저장 후 반환
        // --------------------------------------------------------

        return walkRepository.save(walk);
    }


    // ============================================================
    // 주간 산책 비교
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> weeklyComparison(
            Long userId,
            String timezone
    ) {

        ZoneId zone =
                ZoneId.of("UTC");

        if (timezone != null
                && !timezone.isBlank()) {

            try {

                zone =
                        ZoneId.of(timezone);

            } catch (Exception e) {

                throw new InvalidRequestDataException(
                        ErrorCode.INVALID_TIMEZONE
                );
            }
        }


        // 현재 주의 일요일 00:00:00
        Instant curStart =
                dateUtils.getStartOfWeekSundayInstant(
                        zone
                );

        Instant curEnd =
                curStart.plusSeconds(
                        7 * 24 * 3600
                );


        // 지난 주
        Instant prevStart =
                curStart.minusSeconds(
                        7 * 24 * 3600
                );

        Instant prevEnd =
                curEnd.minusSeconds(
                        7 * 24 * 3600
                );


        // 사용자 반려동물 목록
        List<Pet> pets =
                petService.listByOwner(
                        userId
                );


        Map<Long, String> petNameMap =
                pets.stream()
                        .collect(
                                Collectors.toMap(
                                        Pet::getId,
                                        Pet::getName
                                )
                        );


        // 현재 주 통계
        Map<Long, WalkRepository.WeeklyAggRow> curAgg =
                walkRepository
                        .aggregateByUserAndPeriod(
                                userId,
                                curStart,
                                curEnd
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        WalkRepository.WeeklyAggRow::getPetId,
                                        r -> r
                                )
                        );


        // 지난 주 통계
        Map<Long, WalkRepository.WeeklyAggRow> prevAgg =
                walkRepository
                        .aggregateByUserAndPeriod(
                                userId,
                                prevStart,
                                prevEnd
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        WalkRepository.WeeklyAggRow::getPetId,
                                        r -> r
                                )
                        );


        List<Map<String, Object>> petItems =
                new ArrayList<>();


        for (Pet pet : pets) {

            long petId =
                    pet.getId();


            var current =
                    curAgg.getOrDefault(
                            petId,
                            emptyAgg(petId)
                    );


            var previous =
                    prevAgg.getOrDefault(
                            petId,
                            emptyAgg(petId)
                    );


            long currentWalks =
                    nvl(
                            current.getTotalWalks()
                    );

            double currentDistance =
                    nvl(
                            current.getTotalDistanceKm()
                    );

            long currentDuration =
                    nvl(
                            current.getTotalDurationSec()
                    );


            long previousWalks =
                    nvl(
                            previous.getTotalWalks()
                    );

            double previousDistance =
                    nvl(
                            previous.getTotalDistanceKm()
                    );

            long previousDuration =
                    nvl(
                            previous.getTotalDurationSec()
                    );


            Double walksPct =
                    pct(
                            previousWalks,
                            currentWalks
                    );

            Double distancePct =
                    pct(
                            previousDistance,
                            currentDistance
                    );

            Double durationPct =
                    pct(
                            previousDuration,
                            currentDuration
                    );


            /*
             * Map.of는 null을 허용하지 않으므로
             * pct가 null일 수 있는 delta는 HashMap 사용
             */
            Map<String, Object> delta =
                    new java.util.HashMap<>();

            delta.put(
                    "walksPct",
                    walksPct
            );

            delta.put(
                    "distancePct",
                    distancePct
            );

            delta.put(
                    "durationPct",
                    durationPct
            );


            Map<String, Object> item =
                    Map.of(

                            "petId",
                            petId,

                            "name",
                            petNameMap.getOrDefault(
                                    petId,
                                    "unknown"
                            ),

                            "currentWeekSummary",
                            Map.of(
                                    "totalWalks",
                                    currentWalks,

                                    "totalDistanceKm",
                                    round1(
                                            currentDistance
                                    ),

                                    "totalDurationSec",
                                    currentDuration
                            ),

                            "previousWeekSummary",
                            Map.of(
                                    "totalWalks",
                                    previousWalks,

                                    "totalDistanceKm",
                                    round1(
                                            previousDistance
                                    ),

                                    "totalDurationSec",
                                    previousDuration
                            ),

                            "delta",
                            delta
                    );


            petItems.add(
                    item
            );
        }


        return Map.of(

                "userId",
                userId,

                "timezone",
                zone.getId(),

                "period",
                Map.of(

                        "current",
                        Map.of(
                                "startDate",
                                curStart.toString(),

                                "endDate",
                                curEnd.toString()
                        ),

                        "previous",
                        Map.of(
                                "startDate",
                                prevStart.toString(),

                                "endDate",
                                prevEnd.toString()
                        )
                ),

                "pets",
                petItems
        );
    }


    // ============================================================
    // 이번 주 전체 산책 리스트 조회
    // ============================================================

    @Transactional(readOnly = true)
    public List<Walk> listThisWeekWalks(
            Long userId,
            String timezone
    ) {

        ZoneId zone =
                ZoneId.of("UTC");


        if (timezone != null
                && !timezone.isBlank()) {

            try {

                zone =
                        ZoneId.of(timezone);

            } catch (Exception e) {

                throw new InvalidRequestDataException(
                        ErrorCode.INVALID_TIMEZONE
                );
            }
        }


        Instant startOfWeek =
                dateUtils.getStartOfWeekInstant(
                        zone
                );


        Instant endOfWeek =
                startOfWeek
                        .plusSeconds(
                                7 * 24 * 3600
                        )
                        .minusNanos(1);


        // 사용자 소유 반려동물
        List<Pet> pets =
                petService.listByOwner(
                        userId
                );


        List<Walk> walks =
                new ArrayList<>();


        for (Pet pet : pets) {

            walks.addAll(

                    walkRepository
                            .findByPetIdAndStartTimeBetweenOrderByStartTimeAsc(

                                    pet.getId(),

                                    startOfWeek,

                                    endOfWeek
                            )
            );
        }


        return walks;
    }


    // ============================================================
    // Helper
    // ============================================================

    private static WalkRepository.WeeklyAggRow emptyAgg(
            long petId
    ) {

        return new WalkRepository.WeeklyAggRow() {

            @Override
            public Long getPetId() {
                return petId;
            }

            @Override
            public Long getTotalWalks() {
                return 0L;
            }

            @Override
            public Double getTotalDistanceKm() {
                return 0.0;
            }

            @Override
            public Long getTotalDurationSec() {
                return 0L;
            }
        };
    }


    private static long nvl(
            Long value
    ) {

        return value == null
                ? 0L
                : value;
    }


    private static double nvl(
            Double value
    ) {

        return value == null
                ? 0.0
                : value;
    }


    private static Double pct(
            double previous,
            double current
    ) {

        if (previous == 0) {
            return null;
        }

        return Math.round(
                (
                        (current - previous)
                                / previous
                                * 100.0
                )
                        * 10.0
        ) / 10.0;
    }


    private static double round1(
            double value
    ) {

        return Math.round(
                value * 10.0
        ) / 10.0;
    }


    // ============================================================
    // Offset 기반 Pageable
    // ============================================================

    private static final class OffsetBasedPageable
            implements Pageable {

        private final int offset;
        private final int pageSize;
        private final Sort sort;


        private OffsetBasedPageable(
                int offset,
                int pageSize,
                Sort sort
        ) {

            this.offset = offset;
            this.pageSize = pageSize;
            this.sort = sort;
        }


        @Override
        public int getPageNumber() {

            return offset
                    / pageSize;
        }


        @Override
        public int getPageSize() {

            return pageSize;
        }


        @Override
        public long getOffset() {

            return offset;
        }


        @Override
        public Sort getSort() {

            return sort;
        }


        @Override
        public Pageable next() {

            return new OffsetBasedPageable(
                    offset + pageSize,
                    pageSize,
                    sort
            );
        }


        @Override
        public Pageable previousOrFirst() {

            return hasPrevious()
                    ? new OffsetBasedPageable(
                            offset - pageSize,
                            pageSize,
                            sort
                    )
                    : first();
        }


        @Override
        public Pageable first() {

            return new OffsetBasedPageable(
                    0,
                    pageSize,
                    sort
            );
        }


        @Override
        public Pageable withPage(
                int pageNumber
        ) {

            if (pageNumber < 0) {

                throw new IllegalArgumentException(
                        "Page index must not be less than zero"
                );
            }

            return new OffsetBasedPageable(
                    pageNumber * pageSize,
                    pageSize,
                    sort
            );
        }


        @Override
        public boolean hasPrevious() {

            return offset > 0;
        }
    }
}