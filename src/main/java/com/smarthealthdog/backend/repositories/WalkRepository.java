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

    // 기간 + 최신순/오래된순
    List<Walk> findByPetIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long petId, Instant start, Instant end);
    List<Walk> findByPetIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long petId, Instant start, Instant end);

        @Query(value = """
        SELECT w FROM Walk w
        WHERE w.pet.id = :petId
          AND (:startInclusive IS NULL OR w.startTime >= :startInclusive)
          AND (:endExclusive IS NULL OR w.startTime < :endExclusive)
        """,
        countQuery = """
        SELECT COUNT(w) FROM Walk w
        WHERE w.pet.id = :petId
          AND (:startInclusive IS NULL OR w.startTime >= :startInclusive)
          AND (:endExclusive IS NULL OR w.startTime < :endExclusive)
        """)
        Page<Walk> findByPetIdAndStartTimeRange(
            @Param("petId") Long petId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            Pageable pageable
        );

    /** 주간 합계용 네이티브 프로젝션 */
    public interface WeeklyAggRow {
        Long getPetId();
        Long getTotalWalks();
        Double getTotalDistanceKm();
        Long getTotalDurationSec();
    }

    @Query(value = """
    SELECT
        w.pet_id AS petId,
        COUNT(*) AS totalWalks,
        COALESCE(SUM(w.distance_km), 0) AS totalDistanceKm,
        COALESCE(SUM(
            CASE WHEN w.end_time IS NOT NULL
                THEN EXTRACT(EPOCH FROM (w.end_time - w.start_time))
                ELSE 0 END
        ), 0) AS totalDurationSec
    FROM walks w
    JOIN pets p ON w.pet_id = p.id
    WHERE 
        p.owner_id = :userId 
        AND w.start_time >= :start
        AND w.start_time < :end
    GROUP BY w.pet_id
        """, nativeQuery = true)
    List<WeeklyAggRow> aggregateByUserAndPeriod(
            @Param("userId") Long userId,
            @Param("start") Instant start,
            @Param("end")   Instant end);

}
