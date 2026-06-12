package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Walk;
import com.smarthealthdog.backend.dto.walk.CreateWalkRequest;
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
    private final ObjectMapper objectMapper = new ObjectMapper(); // 간단히 로컬로 사용
    private final DateUtils dateUtils;

    /** 산책 시작 (완료된 코드) */
    @Transactional
    public Walk create(Long petId, Long userId, CreateWalkRequest req) {
        Pet pet = petService.get(petId);

        if (req.endTime().isBefore(req.startTime())) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_WALK_TIME_RANGE);
        }
        if (req.distanceKm().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_WALK_DISTANCE);
        }

        // 5) 좌표 JSON 직렬화
        String pathJson = "[]";
        try {
            if (req.pathCoordinates() != null) {
                pathJson = objectMapper.writeValueAsString(req.pathCoordinates());
            }
        } catch (JsonProcessingException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_WALK_PATH);
        }

        // JPA 변경감지로 자동 update
        Walk walk = Walk.builder()
                .pet(pet)
                .startTime(req.startTime())
                .endTime(req.endTime())
                .distanceKm(req.distanceKm())
                .pathCoordinates(pathJson)
                .build();

        return walkRepository.save(walk);
    }

    public Walk get(Long walkId, Long userId) {
        Walk w = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND));

        if (!w.getPet().getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND);
        }

        return w;
    }

    @Transactional(readOnly = true)
    public List<Walk> listByPet(Long petId, OffsetDateTime start, OffsetDateTime end, String sortBy) {
        String sort = (sortBy == null || sortBy.isBlank()) ? "date_desc" : sortBy;

        if (start == null && end == null) {
            return switch (sort) {
                case "date_asc" -> walkRepository.findByPetIdOrderByStartTimeAsc(petId);
                default         -> walkRepository.findByPetIdOrderByStartTimeDesc(petId);
            };
        }

        OffsetDateTime startSafe = (start != null) ? start : OffsetDateTime.MIN;
        OffsetDateTime endSafe   = (end   != null) ? end   : OffsetDateTime.MAX;

        return switch (sort) {
            case "date_asc" -> walkRepository
                    .findByPetIdAndStartTimeBetweenOrderByStartTimeAsc(petId, startSafe, endSafe);
            default         -> walkRepository
                    .findByPetIdAndStartTimeBetweenOrderByStartTimeDesc(petId, startSafe, endSafe);
        };
    }
    //산책 삭제? 부분 이거는 물어볼 것(참고로 jwt붙이면 userId 파라미터 제거)
    @Transactional
    public void delete(Long walkId, Long userId) {
        var w = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND));

        // 임시 소유권 검증 (JWT 머지 후 인증정보로 교체)
        if (!w.getPet().getOwner().getId().equals(userId)) {
            // 401 성격이지만, 공통 예외체계 없으니 일단 404처럼 감춤 or 별도 UnauthorizedException 만들어도 됨
            throw new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND);
        }

        walkRepository.deleteById(walkId);
    }

    // 산책 종료
    @Transactional
    public Walk end(Long petId, Long walkId, Long userId) {

        // 1) 산책 기록 조회 (없으면 404)
        Walk walk = walkRepository.findById(walkId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND));

        // 2) petId 일치 여부 확인 (URL의 petId랑 실제 walk의 petId가 같은지)
        if (!walk.getPet().getId().equals(petId)) {
            throw new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND);
        }

        // 3) 소유권 검증 (내 반려동물 산책만 종료 가능)
        if (!walk.getPet().getOwner().getId().equals(userId)) {
            // 소유자가 아니면 동일하게 404 처리 (정보 숨김)
            throw new ResourceNotFoundException(ErrorCode.WALK_NOT_FOUND);
        }

        // 4) 이미 종료된 산책이면 그대로 반환
        if (walk.getEndTime() != null) {
            return walk;
        }

        // 5) 산책 종료 처리 (엔티티의 end(...) 메서드가 endTime, durationSeconds 계산)
        walk.end(Instant.now());

        // 6) 저장 후 반환
        return walkRepository.save(walk);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> weeklyComparison(
            Long userId, String timezone) {
        
        // 날짜 계산을 위해 curStart를 해당 주의 일요일로 이동
        ZoneId zone = ZoneId.of("UTC");
        if (timezone != null && !timezone.isBlank()) {
            try {
                zone = ZoneId.of(timezone);
            } catch (Exception e) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_TIMEZONE);
            }
        }

        // 현재 주의 일요일 00:00:00 Instant
        Instant curStart = dateUtils.getStartOfWeekSundayInstant(zone);
        Instant curEnd = curStart.plusSeconds(7 * 24 * 3600); // 주말 포함 끝나는 시각

        // 지난 주: 각 -7일
        Instant prevStart = curStart.minusSeconds(7 * 24 * 3600);
        Instant prevEnd = curEnd.minusSeconds(7 * 24 * 3600);

        // 사용자 반려동물 목록(이름 매핑 용)
        List<Pet> pets = petService.listByOwner(userId);
        Map<Long, String> petNameMap = pets.stream()
                .collect(Collectors.toMap(Pet::getId, Pet::getName));

        // 기간별 집계
        Map<Long, WalkRepository.WeeklyAggRow> curAgg = walkRepository
                .aggregateByUserAndPeriod(userId, curStart, curEnd)
                .stream().collect(Collectors.toMap(WalkRepository.WeeklyAggRow::getPetId, r -> r));

        Map<Long, WalkRepository.WeeklyAggRow> prevAgg = walkRepository
                .aggregateByUserAndPeriod(userId, prevStart, prevEnd)
                .stream().collect(Collectors.toMap(WalkRepository.WeeklyAggRow::getPetId, r -> r));

        // 응답 구성
        List<Map<String, Object>> petItems = new ArrayList<>();
        for (Pet p : pets) {
            long petId = p.getId();

            var c = curAgg.getOrDefault(petId, emptyAgg(petId));
            var v = prevAgg.getOrDefault(petId, emptyAgg(petId));

            long cWalks = nvl(c.getTotalWalks());
            double cDist = nvl(c.getTotalDistanceKm());
            long cDur   = nvl(c.getTotalDurationSec());

            long vWalks = nvl(v.getTotalWalks());
            double vDist = nvl(v.getTotalDistanceKm());
            long vDur   = nvl(v.getTotalDurationSec());

            // 💡 퍼센트 먼저 변수에 담기
            Double walksPct    = pct(vWalks, cWalks);
            Double distancePct = pct(vDist,  cDist);
            Double durationPct = pct(vDur,   cDur);
            /* 
            Map<String, Object> item = Map.of(
                "petId", petId,
                "name",   petNameMap.getOrDefault(petId, "unknown"),
                "currentWeekSummary", Map.of(
                    "totalWalks",        cWalks,
                    "totalDistanceKm",  round1(cDist),
                    "totalDurationSec", cDur
                ),
                "previousWeekSummary", Map.of(
                    "totalWalks",        vWalks,
                    "totalDistanceKm",  round1(vDist),
                    "totalDurationSec", vDur
                ),
                "delta", Map.of(
                    "walksPct",    pct(vWalks, cWalks),
                    "distancePct", pct(vDist,  cDist),
                    "durationPct", pct(vDur,   cDur)
                )
            );
            petItems.add(item);
            */
            // 💡 Map.of 대신 HashMap 사용 (null 허용)
            Map<String, Object> delta = new java.util.HashMap<>();
            delta.put("walksPct",    walksPct);
            delta.put("distancePct", distancePct);
            delta.put("durationPct", durationPct);

            Map<String, Object> item = Map.of(
                "petId", petId,
                "name",   petNameMap.getOrDefault(petId, "unknown"),
                "currentWeekSummary", Map.of(
                    "totalWalks",        cWalks,
                    "totalDistanceKm",  round1(cDist),
                    "totalDurationSec", cDur
                ),
                "previousWeekSummary", Map.of(
                    "totalWalks",        vWalks,
                    "totalDistanceKm",  round1(vDist),
                    "totalDurationSec", vDur
                ),
                "delta", delta   // ← 여기에는 HashMap이 들어감
            );
            petItems.add(item);
        }

        return Map.of(
            "userId", userId,
            "timezone", zone.getId(),
            "period", Map.of(
                "current",  Map.of("startDate", curStart.toString(), "endDate", curEnd.toString()),
                "previous", Map.of("startDate", prevStart.toString(), "endDate", prevEnd.toString())
            ),
            "pets", petItems
        );
    }

    //이번주 전체 산책 리스트 조회 api(구현
    @Transactional(readOnly = true)
    public List<Walk> listThisWeekWalks(Long userId, String timezone) {
        ZoneId zone = ZoneId.of("UTC");
        if (timezone != null && !timezone.isBlank()) {
            try {
                zone = ZoneId.of(timezone);
            } catch (Exception e) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_TIMEZONE);
            }
        }

        // 이번 주 시작(월요일 0시)과 종료(일요일 23:59:59) 계산
        Instant startOfWeek = dateUtils.getStartOfWeekInstant(zone);
        Instant endOfWeek = startOfWeek.plusSeconds(7 * 24 * 3600).minusSeconds(1);

        // 사용자 소유 반려동물 전체 목록
        List<Pet> pets = petService.listByOwner(userId);

        // 이번 주 산책 리스트 전체 수집
        List<Walk> walks = new ArrayList<>();
        for (Pet pet : pets) {
            walks.addAll(walkRepository.findByPetIdAndStartTimeBetweenOrderByStartTimeAsc(
                    pet.getId(),
                    startOfWeek.atOffset(ZoneOffset.UTC),
                    endOfWeek.atOffset(ZoneOffset.UTC)
            ));
        }

        return walks;
    }

    // === helpers ===
    private static WalkRepository.WeeklyAggRow emptyAgg(long petId) {
        return new WalkRepository.WeeklyAggRow() {
            public Long getPetId() { return petId; }
            public Long getTotalWalks() { return 0L; }
            public Double getTotalDistanceKm() { return 0.0; }
            public Long getTotalDurationSec() { return 0L; }
        };
    }

    private static long nvl(Long v)    { return v == null ? 0L  : v; }
    private static double nvl(Double v){ return v == null ? 0.0 : v; }

    private static Double pct(double prev, double curr) {
        if (prev == 0) return null; // 정책: 지난주 0이면 null (원하면 100.0로 변경 가능)
        return Math.round(((curr - prev) / prev * 100.0) * 10.0) / 10.0; // 소수1자리 반올림
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
