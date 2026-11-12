package com.smarthealthdog.backend.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    // Foreign Key: Links this submission directly to a specific pet
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 255)
    @Builder.Default
    private SubmissionTypeEnum type = SubmissionTypeEnum.EYE;

    @NotNull
    @Column(name = "photo_url", nullable = false, columnDefinition = "TEXT")
    private String photoUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @NotNull
    @Column(name = "submitted_at", updatable = false, nullable = false)
    private Instant submittedAt;

    /**
     * PROCESSING 으로 전환된 시각. 큐에 다시 실릴 때마다 갱신된다.
     *
     * 재시도 타임아웃은 이 값을 기준으로 잰다 — submittedAt 은 제출 시각이라
     * 재시도해도 바뀌지 않으므로, 그것으로 재면 2회차부터 타임아웃이 아니라
     * 재시도 잡 주기만큼만 주어진다.
     *
     * NULL 이면 아직 큐에 실린 적이 없다는 뜻이다.
     */
    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason")
    @Enumerated(EnumType.STRING)
    private SubmissionFailureReasonEnum failureReason;

    @Column(name = "celery_task_string", columnDefinition = "TEXT")
    private String celeryTaskString;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Diagnosis> diagnoses;
}
