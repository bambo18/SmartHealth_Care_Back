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

    /*
     * 일시정지를 제외한 실제 활동 시간(초)
     *
     * 앱이 계산한 값을 그대로 저장한다.
     *
     * null 허용:
     * - 활동 시간을 전달하지 않은 종료 요청
     * - 이 기능 도입 이전에 기록된 과거 산책
     *
     * 이 경우 산책 시간은 기존 방식(endTime - startTime)으로 계산된다.
     */
    @Column(name = "active_duration_seconds")
    private Long activeDurationSeconds;

    @Builder
    private Walk(
            Pet pet,
            Instant startTime,
            Instant endTime,
            BigDecimal distanceKm,
            String pathCoordinates,
            Long activeDurationSeconds
    ) {
        this.pet = pet;
        this.startTime = startTime;
        this.endTime = endTime;
        this.activeDurationSeconds = activeDurationSeconds;

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
     * 활동 시간(activeDurationSeconds)이 저장되어 있으면 그 값을 우선 반환한다.
     * 즉 일시정지(휴식) 시간은 제외된다.
     *
     * 활동 시간이 없으면 기존 방식대로 전체 경과 시간(endTime - startTime)을 반환한다.
     *
     * 진행 중인 산책은 endTime이 없기 때문에 null 반환
     */
    public Long getDurationSeconds() {
        if (activeDurationSeconds != null) {
            return activeDurationSeconds;
        }

        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).getSeconds();
        }

        return null;
    }

    /*
     * 전체 경과 시간(초)
     *
     * 휴식 시간을 포함한 시작 ~ 종료 사이의 시간
     */
    public Long getElapsedSeconds() {
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
        complete(
                endTime,
                distanceKm,
                pathCoordinates,
                null
        );
    }

    /*
     * 산책 종료 처리 (활동 시간 포함)
     *
     * activeDurationSeconds:
     * 앱이 계산한, 일시정지를 제외한 실제 활동 시간(초)
     *
     * null이면 기존 값을 유지하므로
     * 활동 시간을 전달하지 않은 요청은 기존 계산 방식(경과 시간)을 그대로 사용한다.
     */
    public void complete(
            Instant endTime,
            BigDecimal distanceKm,
            String pathCoordinates,
            Long activeDurationSeconds
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

        if (activeDurationSeconds != null) {
            this.activeDurationSeconds = activeDurationSeconds;
        }
    }
}