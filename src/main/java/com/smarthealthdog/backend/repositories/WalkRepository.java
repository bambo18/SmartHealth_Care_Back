package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.Walk;

public interface WalkRepository extends JpaRepository<Walk, Long> {

    // ============================================================
    // 산책 목록 조회
    // ============================================================

    /**
     * 날짜 조건 없음
     */
    Page<Walk> findByPetId(
            Long petId,
            Pageable pageable
    );

    /**
     * 시작 날짜만 있음
     * startTime >= startInclusive
     */
    Page<Walk> findByPetIdAndStartTimeGreaterThanEqual(
            Long petId,
            Instant startInclusive,
            Pageable pageable
    );

    /**
     * 종료 날짜만 있음
     * startTime < endExclusive
     */
    Page<Walk> findByPetIdAndStartTimeLessThan(
            Long petId,
            Instant endExclusive,
            Pageable pageable
    );

    /**
     * 시작 날짜 + 종료 날짜 모두 있음
     * startTime >= startInclusive
     * startTime < endExclusive
     */
    Page<Walk> findByPetIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            Long petId,
            Instant startInclusive,
            Instant endExclusive,
            Pageable pageable
    );


    // ============================================================
    // 기간 조회
    // ============================================================

    List<Walk> findByPetIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long petId,
            Instant start,
            Instant end
    );

    List<Walk> findByPetIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long petId,
            Instant start,
            Instant end
    );


    // ============================================================
    // 주간 합계용 네이티브 프로젝션
    // ============================================================

    interface WeeklyAggRow {

        Long getPetId();

        Long getTotalWalks();

        Double getTotalDistanceKm();

        Long getTotalDurationSec();
    }


    @Query(
            value = """
                    SELECT
                        w.pet_id AS petId,
                        COUNT(*) AS totalWalks,
                        COALESCE(SUM(w.distance_km), 0) AS totalDistanceKm,
                        COALESCE(SUM(
                            CASE
                                WHEN w.end_time IS NOT NULL
                                THEN EXTRACT(EPOCH FROM (w.end_time - w.start_time))
                                ELSE 0
                            END
                        ), 0) AS totalDurationSec
                    FROM walks w
                    JOIN pets p
                        ON w.pet_id = p.id
                    WHERE
                        p.owner_id = :userId
                        AND w.start_time >= :start
                        AND w.start_time < :end
                    GROUP BY w.pet_id
                    """,
            nativeQuery = true
    )
    List<WeeklyAggRow> aggregateByUserAndPeriod(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}