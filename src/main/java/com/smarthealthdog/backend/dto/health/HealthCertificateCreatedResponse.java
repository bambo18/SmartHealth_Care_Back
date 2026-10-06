package com.smarthealthdog.backend.dto.health;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * POST /api/pets/{id}/submissions/certificate 의 201 본문.
 *
 * 기존 POST .../submissions/eye 는 201 에 빈 본문을 돌려줘서
 * 클라이언트가 방금 만든 리소스를 알 수 없었다. 같은 실수를 반복하지 않는다.
 *
 * result 를 @JsonUnwrapped 로 평탄화해 명세의 평면 JSON 모양을 맞추면서도
 * 필드 매핑을 HealthCertificateResult 한 곳에만 둔다.
 */
public record HealthCertificateCreatedResponse(
    @JsonProperty("submission_id") UUID submissionId,
    @JsonProperty("pet_id") Long petId,
    @JsonProperty("submitted_at") Instant submittedAt,
    @JsonUnwrapped HealthCertificateResult result
) {}
