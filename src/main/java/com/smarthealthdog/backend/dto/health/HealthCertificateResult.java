package com.smarthealthdog.backend.dto.health;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 진단서 결과 — 12개 항목 + 메타.
 *
 * SubmissionDetail&lt;T&gt; 의 results 요소로도 쓰이고,
 * 201 / PATCH 응답 안에도 들어간다.
 *
 * 견주 성명 주소 필드는 없다 (SPEC 1.5 범위 밖).
 */
public record HealthCertificateResult(
    @JsonProperty("animal_name") String animalName,
    @JsonProperty("species_text") String speciesText,
    @JsonProperty("breed") String breed,
    @JsonProperty("gender_text") String genderText,
    @JsonProperty("coat_color") String coatColor,
    @JsonProperty("age_text") String ageText,
    @JsonProperty("features") String features,
    @JsonProperty("disease_name") String diseaseName,

    /** 파싱 성공 시에만 값이 있다. null 이어도 아래 원문은 항상 있다. */
    @JsonProperty("onset_date") LocalDate onsetDate,
    @JsonProperty("onset_date_text") String onsetDateText,
    @JsonProperty("diagnosed_date") LocalDate diagnosedDate,
    @JsonProperty("diagnosed_date_text") String diagnosedDateText,

    @JsonProperty("prognosis") String prognosis,
    @JsonProperty("remarks") String remarks,
    @JsonProperty("ocr_confidence") BigDecimal ocrConfidence,
    @JsonProperty("is_manually_edited") boolean manuallyEdited
) {}
