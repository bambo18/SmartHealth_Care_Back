
package com.smarthealthdog.backend.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "health_check_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthCheckRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(name = "species", nullable = false, length = 10)
    private PetSpecies species;

    @Column(name = "overall_stage", nullable = false)
    private Short overallStage;

    @Column(name = "scoring_version", nullable = false, length = 30)
    private String scoringVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private HealthCheckRecord(
            Pet pet,
            PetSpecies species,
            Short overallStage,
            String scoringVersion
    ) {
        this.pet = pet;
        this.species = species;
        this.overallStage = overallStage;
        this.scoringVersion = scoringVersion == null
                ? "v1"
                : scoringVersion;
    }
}
