package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.Walk;

public interface WalkRepository extends JpaRepository<Walk, Long> {

    // 전체 + 최신순/오래된순
    List<Walk> findByPetIdOrderByStartTimeDesc(Long petId);
    List<Walk> findByPetIdOrderByStartTimeAsc(Long petId);

    // 기간 + 최신순/오래된순
    List<Walk> findByPetIdAndStartTimeBetweenOrderByStartTimeDesc(
            Long petId, OffsetDateTime start, OffsetDateTime end);
    List<Walk> findByPetIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long petId, OffsetDateTime start, OffsetDateTime end);

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
