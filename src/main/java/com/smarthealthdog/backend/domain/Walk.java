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

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;   // 종료 시점 저장

    @Column(name = "distance_km", nullable = false, precision = 7, scale = 2)
    private BigDecimal distanceKm;          // 총 거리 (단위는 정책에 맞춰 사용)

    @Column(columnDefinition = "TEXT", name = "path_coordinates")
    private String pathCoordinates;   // 경로(JSON 문자열)

    @Builder
    private Walk(Pet pet, Instant startTime, Instant endTime, BigDecimal distanceKm, String pathCoordinates) {
        this.pet = pet;
        this.startTime = startTime;
        this.endTime = endTime;
        this.distanceKm = distanceKm;
        this.pathCoordinates = pathCoordinates;
    }

    public Long getDurationSeconds() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).getSeconds();
        }
        return null;
    }

    public void end(Instant endTime) {
        if (endTime == null) {
            return;
        }

        this.endTime = endTime;

        // durationSeconds는 startTime과 endTime 차이로 계산됨
        // 현재 durationSeconds 필드가 없기 때문에
        // getDurationSeconds()로 계산해서 사용
    }
}
