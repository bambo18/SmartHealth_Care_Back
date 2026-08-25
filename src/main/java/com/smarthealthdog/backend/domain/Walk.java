package com.smarthealthdog.backend.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "walks")
public class Walk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /*
     * 산책 진행 중에는 아직 종료 시간이 없으므로 null 허용
     *
     * 산책 시작:
     * endTime = null
     *
     * 산책 종료:
     * endTime = 실제 종료 시간
     */
    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "distance_km", nullable = false, precision = 7, scale = 2)
    private BigDecimal distanceKm;

    @Column(columnDefinition = "TEXT", name = "path_coordinates")
    private String pathCoordinates;

    @Builder
    private Walk(
            Pet pet,
            Instant startTime,
            Instant endTime,
            BigDecimal distanceKm,
            String pathCoordinates
    ) {
        this.pet = pet;
        this.startTime = startTime;
        this.endTime = endTime;

        this.distanceKm = distanceKm != null
                ? distanceKm
                : BigDecimal.ZERO;

        this.pathCoordinates = pathCoordinates != null
                ? pathCoordinates
                : "[]";
    }

    /*
     * 산책 시간(초)
     *
     * 진행 중인 산책은 endTime이 없기 때문에 null 반환
     */
    public Long getDurationSeconds() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).getSeconds();
        }

        return null;
    }

    /*
     * 기존 코드와의 호환성을 위해 유지
     */
    public void end(Instant endTime) {
        if (endTime == null) {
            return;
        }

        this.endTime = endTime;
    }

    /*
     * 산책 종료 처리
     *
     * 종료 시점에
     * - 종료 시간
     * - 최종 이동 거리
     * - 최종 이동 경로
     *
     * 를 한 번에 저장
     */
    public void complete(
            Instant endTime,
            BigDecimal distanceKm,
            String pathCoordinates
    ) {
        if (endTime != null) {
            this.endTime = endTime;
        }

        if (distanceKm != null) {
            this.distanceKm = distanceKm;
        }

        if (pathCoordinates != null) {
            this.pathCoordinates = pathCoordinates;
        }
    }
}