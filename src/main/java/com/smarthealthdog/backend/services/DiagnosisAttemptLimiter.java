package com.smarthealthdog.backend.services;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 유료 외부 OCR 호출을 발생시키는 요청의 빈도를 사용자·유형별로 제한한다.
 *
 * 제한 대상은 {@code Submission} 행이 아니라 "시도"다. 건강검진표 OCR 은 인식에 실패하면
 * 제출 행을 만들지 않으므로, 제출 행을 세면 비용을 발생시킨 실패 요청만 정확히 제한을
 * 빠져나간다. 그래서 인식 결과를 보기 전에 시도를 먼저 기록한다.
 */
@Service
@RequiredArgsConstructor
public class DiagnosisAttemptLimiter {

    private final DiagnosisAttemptRepository diagnosisAttemptRepository;

    @Value("${ocr.user-interval.seconds}")
    private int userIntervalSeconds;

    /**
     * 제한을 검사하고, 통과하면 시도를 기록한다.
     *
     * 호출자의 트랜잭션과 분리된 새 트랜잭션에서 커밋한다 — 이후 단계가 실패해도
     * 시도 기록은 남아야 제한이 의미를 가진다.
     *
     * @param userId 사용자 ID
     * @param type 제출 유형
     * @throws InvalidRequestDataException 제한 간격 안에 이미 시도가 있는 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecordAttempt(Long userId, SubmissionTypeEnum type) {
        if (userId == null || type == null) {
            throw new IllegalArgumentException("User ID and submission type must not be null.");
        }

        Instant threshold = Instant.now().minus(Duration.ofSeconds(userIntervalSeconds));

        if (diagnosisAttemptRepository.existsByUserIdAndTypeAndAttemptedAtAfter(userId, type, threshold)) {
            throw new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT);
        }

        // 제한 간격을 지난 기록은 쓸모가 없다. 시도를 남길 때마다 정리해 테이블을 작게 유지한다.
        diagnosisAttemptRepository.deleteStaleAttempts(userId, type, threshold);

        diagnosisAttemptRepository.save(
            DiagnosisAttempt.builder()
                .userId(userId)
                .type(type)
                .attemptedAt(Instant.now())
                .build()
        );
    }
}
