package com.smarthealthdog.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 진단 요청 빈도 제한을 위한 "시도" 기록.
 *
 * Submission 과 달리 판별 성공 여부와 무관하게 남는다.
 * (petId, type) 당 한 행만 유지하며 갱신만 한다.
 *
 * Pet · User 연관을 걸지 않고 ID 만 보관한다 —
 * 빈도 검사는 ID 만 필요하고, 연관을 걸면 불필요한 조인을 끌고 온다.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "diagnosis_attempts",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_diagnosis_attempts_pet_type",
        columnNames = {"pet_id", "type"}
    )
)
public class DiagnosisAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 255)
    private SubmissionTypeEnum type;

    @Column(name = "last_attempted_at", nullable = false)
    private Instant lastAttemptedAt;
}
