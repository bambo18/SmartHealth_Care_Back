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
@Table(name = "oral_health_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OralHealthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodontal_stage", nullable = false, length = 20)
    private PeriodontalStage periodontalStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "tartar_level", length = 20)
    private TartarLevel tartarLevel;

    @Column(name = "gingiva_inflammation")
    private Boolean gingivaInflammation;

    @Column(name = "halitosis")
    private Boolean halitosis;

    @Column(name = "tooth_loss_count", nullable = false)
    private Integer toothLossCount;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Builder
    private OralHealthRecord(
            Pet pet,
            PeriodontalStage periodontalStage,
            TartarLevel tartarLevel,
            Boolean gingivaInflammation,
            Boolean halitosis,
            Integer toothLossCount,
            String note
    ) {
        this.pet = pet;
        this.periodontalStage = periodontalStage;
        this.tartarLevel = tartarLevel;
        this.gingivaInflammation = gingivaInflammation;
        this.halitosis = halitosis;
        this.toothLossCount = toothLossCount;
        this.note = note;
    }
}