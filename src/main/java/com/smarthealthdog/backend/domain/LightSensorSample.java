package com.smarthealthdog.backend.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "light_sensor_samples")
public class LightSensorSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * 어떤 산책에서 측정한 조도인지
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "walk_id", nullable = false)
    private Walk walk;

    /*
     * 스마트폰에서 각 측정값마다 생성하는 UUID
     *
     * 네트워크 재전송 시 같은 데이터가 중복 저장되는 것을
     * 방지하기 위해 사용
     */
    @Column(name = "client_sample_id", nullable = false)
    private UUID clientSampleId;

    /*
     * 스마트폰에서 실제 측정한 시각
     */
    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    /*
     * 실제 조도 센서 측정값
     * 단위: Lux
     */
    @Column(name = "lux", nullable = false)
    private Double lux;

    /*
     * 서버 DB 저장 시간
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private LightSensorSample(
            Walk walk,
            UUID clientSampleId,
            Instant measuredAt,
            Double lux
    ) {
        this.walk = walk;
        this.clientSampleId = clientSampleId;
        this.measuredAt = measuredAt;
        this.lux = lux;
    }
}