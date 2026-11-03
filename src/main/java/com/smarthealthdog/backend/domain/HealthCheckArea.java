
package com.smarthealthdog.backend.domain;

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
@Entity
@Table(name = "health_check_areas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthCheckArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private HealthCheckRecord record;

    @Column(name = "area_key", nullable = false, length = 30)
    private String areaKey;

    @Column(name = "area_title", nullable = false, length = 100)
    private String areaTitle;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "stage", nullable = false)
    private Short stage;

    @Column(name = "urgent_hit", nullable = false)
    private Boolean urgentHit;

    @Builder
    private HealthCheckArea(
            HealthCheckRecord record,
            String areaKey,
            String areaTitle,
            Integer score,
            Short stage,
            Boolean urgentHit
    ) {
        this.record = record;
        this.areaKey = areaKey;
        this.areaTitle = areaTitle;
        this.score = score;
        this.stage = stage;
        this.urgentHit = urgentHit == null
                ? false
                : urgentHit;
    }
}
