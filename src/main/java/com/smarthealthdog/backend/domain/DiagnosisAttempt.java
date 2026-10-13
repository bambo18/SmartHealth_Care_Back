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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 진단 요청 "시도" 기록.
 *
 * 건강검진표 OCR 은 인식 실패 시 {@link Submission} 행을 만들지 않으므로,
 * 제출 행을 기준으로 빈도를 제한하면 비용을 발생시킨 실패 요청만 제한을 빠져나간다.
 * 성공·실패와 무관하게 남는 기록이 필요해 별도 테이블로 둔다.
 */
@Entity
@Table(name = "diagnosis_attempts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 255)
    private SubmissionTypeEnum type;

    @Column(name = "attempted_at", nullable = false)
    @Builder.Default
    private Instant attemptedAt = Instant.now();
}
