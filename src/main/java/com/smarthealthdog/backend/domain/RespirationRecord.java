package com.smarthealthdog.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "respiration_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RespirationRecord {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "expected_size_bytes", nullable = false)
    private Long expectedSizeBytes;

    @Column(name = "actual_size_bytes")
    private Long actualSizeBytes;

    @Column(name = "duration_sec", nullable = false)
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RespirationStatus status;

    @Column(name = "respiratory_rate", precision = 6, scale = 2)
    private BigDecimal respiratoryRate;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public RespirationRecord(UUID id, Pet pet, String storageKey,
                             long expectedSizeBytes, int durationSec) {
        this.id = id;
        this.pet = pet;
        this.storageKey = storageKey;
        this.expectedSizeBytes = expectedSizeBytes;
        this.durationSec = durationSec;
        this.status = RespirationStatus.UPLOADING;
    }

    public void markUploaded(long actualSizeBytes) {
        if (status != RespirationStatus.UPLOADING) {
            throw new IllegalStateException("Respiration video cannot be marked uploaded in state " + status);
        }
        this.actualSizeBytes = actualSizeBytes;
        this.uploadedAt = Instant.now();
        this.status = RespirationStatus.PENDING;
    }
}
