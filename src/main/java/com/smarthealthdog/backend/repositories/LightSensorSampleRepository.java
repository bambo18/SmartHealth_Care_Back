package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.LightSensorSample;

public interface LightSensorSampleRepository
        extends JpaRepository<LightSensorSample, Long> {

    /*
     * 조도 데이터 저장
     *
     * 같은 walk_id + client_sample_id가 이미 존재한다면
     * PostgreSQL의 ON CONFLICT DO NOTHING을 이용해
     * 오류 없이 무시한다.
     *
     * 반환값:
     *
     * 1 = 신규 저장
     * 0 = 중복 데이터
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO light_sensor_samples (
                walk_id,
                client_sample_id,
                measured_at,
                lux
            )
            VALUES (
                :walkId,
                :clientSampleId,
                :measuredAt,
                :lux
            )
            ON CONFLICT (
                walk_id,
                client_sample_id
            )
            DO NOTHING
            """,
        nativeQuery = true
    )
    int insertIgnoreDuplicate(
            @Param("walkId") Long walkId,
            @Param("clientSampleId") UUID clientSampleId,
            @Param("measuredAt") Instant measuredAt,
            @Param("lux") Double lux
    );

    /*
     * 특정 반려동물의 특정 시간 범위 조도 데이터 조회
     *
     * 예:
     * 오늘 08:00 ~ 현재시간 또는 17:00
     */
    @Query("""
        SELECT s
        FROM LightSensorSample s
        JOIN FETCH s.walk w
        JOIN FETCH w.pet p
        WHERE p.id = :petId
          AND s.measuredAt >= :start
          AND s.measuredAt < :end
        ORDER BY s.measuredAt ASC
        """)
    List<LightSensorSample> findByPetAndMeasuredAtBetween(
            @Param("petId") Long petId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}