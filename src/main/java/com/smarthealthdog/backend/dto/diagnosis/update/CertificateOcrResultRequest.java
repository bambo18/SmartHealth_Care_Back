package com.smarthealthdog.backend.dto.diagnosis.update;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 워커가 진단서 OCR 결과를 돌려줄 때 보내는 본문.
 *
 * {@code PATCH /api/submissions/{submissionId}/certificate/result}
 *
 * <p>기존 눈·소변 콜백({@code SubmissionResultRequest} 등)과 같은 규약을 따른다 —
 * {@code AI_MODEL_SERVICE} 계정으로 인증하고, 필드명은 가공 없는 camelCase 다.
 * 제출 ID 는 경로에 있으므로 본문에 담지 않는다.
 *
 * <p>성공이면 {@code rawText} 와 {@code confidence} 가 반드시 있어야 하고,
 * 실패면 {@code error} 가 반드시 있어야 한다({@link #isConsistent()}).
 *
 * <p><b>이 규격은 AI 측 회신 전까지 확정이 아니다.</b> 특히 {@code confidence} 제공
 * 여부가 미확정이다 — 자세한 내용은 추적되지 않는 로컬 문서 {@code Notion_AI연동협의.md} §2-1.
 */
@Data
public class CertificateOcrResultRequest {

    /** 추출 성공 여부. */
    @NotNull
    private Boolean success;

    /**
     * 인식된 전체 텍스트. 성공 시 필수.
     *
     * 항목으로 쪼개지 않은 원문 그대로를 보낸다. 백엔드가 이 값을 보관하므로
     * 추출 규칙이 개선되면 Vision 을 다시 호출하지 않고 재파싱할 수 있다.
     */
    private String rawText;

    /**
     * 전체 평균 신뢰도 (0.0 ~ 1.0). 성공 시 필수.
     *
     * 백엔드의 인식 품질 게이트가 이 값을 쓴다. 엔티티
     * {@code pet_health_certificates.ocr_confidence} 가 NOT NULL 이라
     * 이 값이 없으면 기록 자체를 저장할 수 없다.
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal confidence;

    /**
     * 워커가 뽑아낸 12개 항목. <b>선택.</b>
     *
     * 생략하면 백엔드가 {@code rawText} 에서 직접 추출한다. 값이 있으면
     * null 이 아닌 항목만 백엔드 추출 결과보다 우선한다.
     */
    @Valid
    private CertificateOcrFieldsDto fields;

    /** 실패 코드. 실패 시 필수 (INVALID_IMAGE, NO_TEXT_DETECTED, OCR_TIMEOUT 등). */
    private String error;

    /**
     * 실패 원인 요약. 선택이며 <b>로그 전용</b> — 사용자에게 노출하지 않는다.
     *
     * Google 원문 예외 메시지를 그대로 담지 않는다.
     */
    private String errorDetail;

    /**
     * 성공·실패 조합이 앞뒤가 맞는지 검사한다.
     *
     * 성공인데 텍스트가 없거나, 실패인데 코드가 없는 본문을 400 으로 막는다.
     */
    @JsonIgnore
    @AssertTrue(message = "success 값과 본문 구성이 일치하지 않습니다.")
    public boolean isConsistent() {
        if (success == null) {
            return true; // @NotNull 이 따로 잡는다
        }

        if (success) {
            return rawText != null && !rawText.isBlank() && confidence != null;
        }

        return error != null && !error.isBlank();
    }
}
