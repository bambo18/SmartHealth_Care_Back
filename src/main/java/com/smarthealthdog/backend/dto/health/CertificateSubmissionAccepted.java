package com.smarthealthdog.backend.dto.health;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;

/**
 * 진단서 업로드 접수 응답.
 *
 * 비동기 처리라 등록 시점에는 추출 결과가 없다. 제출 ID 만 돌려주고, 프론트는 이 ID 로
 * {@code GET /api/submissions/{id}/certificate} 를 조회해 완료를 확인한다.
 *
 * 결과가 담긴 응답은 {@link HealthCertificateResult} 이며 조회·수정에서만 쓰인다.
 */
public record CertificateSubmissionAccepted(
    @JsonProperty("submission_id") UUID submissionId,
    @JsonProperty("pet_id") Long petId,
    @JsonProperty("status") SubmissionStatus status,
    @JsonProperty("submitted_at") Instant submittedAt
) {

    /**
     * 접수된 제출을 응답 객체로 변환한다.
     * @param submission 반려동물 연관이 초기화된 제출
     * @return 접수 응답
     */
    public static CertificateSubmissionAccepted from(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("제출이 null일 수 없습니다.");
        }

        return new CertificateSubmissionAccepted(
            submission.getId(),
            submission.getPet().getId(),
            submission.getStatus(),
            submission.getSubmittedAt()
        );
    }
}
