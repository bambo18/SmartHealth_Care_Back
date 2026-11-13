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

    @Column(name = "celery_task_id")
    private UUID celeryTaskId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    public RespirationRecord(UUID id, Pet pet, String storageKey,
                             long expectedSizeBytes, int durationSec) {
        this.id = id;
        this.pet = pet;
        this.storageKey = storageKey;
        this.expectedSizeBytes = expectedSizeBytes;
        this.durationSec = durationSec;
        this.status = RespirationStatus.UPLOADING;
    }

    // Called by the dispatcher only after a successful Redis enqueue.
    public void markProcessing(UUID taskId) {
        if (status != RespirationStatus.PENDING || uploadedAt == null) {
            throw new IllegalStateException("Respiration record is not ready for inference: " + status);
        }
        if (taskId == null) {
            throw new IllegalArgumentException("Celery task ID is required");
        }
        this.celeryTaskId = taskId;
        this.processingStartedAt = Instant.now();
        this.status = RespirationStatus.PROCESSING;
    }

    // Called only after AI Worker completes a PROCESSING record.
    public void markCompleted(BigDecimal rate) {
        if (status != RespirationStatus.PROCESSING) {
            throw new IllegalStateException("Respiration record is not PROCESSING: " + status);
        }
        if (rate == null || rate.signum() <= 0 || rate.scale() > 2 || rate.precision() - rate.scale() > 4) {
            throw new IllegalArgumentException("Invalid respiratory rate");
        }
        this.respiratoryRate = rate;
        this.failureReason = null;
        this.completedAt = Instant.now();
        this.status = RespirationStatus.COMPLETED;
    }

    /**
     * AI team's two-step callback: save BPM while keeping PROCESSING.
     * Only positive integral BPM values supported by numeric(6,2) are accepted.
     */
    public void saveMeasuredRate(BigDecimal rate) {
        if (status != RespirationStatus.PROCESSING) {
            throw new IllegalStateException("Respiration record is not PROCESSING: " + status);
        }
        if (rate == null || rate.signum() <= 0 ||
                rate.compareTo(BigDecimal.valueOf(9999)) > 0 ||
                rate.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Respiratory rate must be a positive integer up to 9999");
        }
        if (this.respiratoryRate != null && this.respiratoryRate.compareTo(rate) != 0) {
            throw new IllegalStateException("Conflicting respiratory rate already saved");
        }
        this.respiratoryRate = rate;
        this.failureReason = null;
        // completedAt and status are intentionally unchanged until a second PATCH.
    }

    /** Complete a two-step callback after a rate has been saved. */
    public void markCompletedFromSavedRate() {
        if (status != RespirationStatus.PROCESSING) {
            throw new IllegalStateException("Respiration record is not PROCESSING: " + status);
        }
        if (respiratoryRate == null) {
            throw new IllegalStateException("Cannot complete respiration without a saved respiratory rate");
        }
        this.failureReason = null;
        this.completedAt = Instant.now();
        this.status = RespirationStatus.COMPLETED;
    }

    // A failed inference does not produce a respiratory rate.
    public void markInferenceFailed(String reason) {
        if (status != RespirationStatus.PROCESSING) {
            throw new IllegalStateException("Respiration record is not PROCESSING: " + status);
        }
        if (reason == null || reason.isBlank() || reason.length() > 256) {
            throw new IllegalArgumentException("Invalid failure reason");
        }
        this.respiratoryRate = null;
        this.failureReason = reason;
        this.completedAt = Instant.now();
        this.status = RespirationStatus.FAILED;
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
