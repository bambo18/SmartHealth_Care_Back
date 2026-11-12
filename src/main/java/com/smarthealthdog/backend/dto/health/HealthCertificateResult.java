package com.smarthealthdog.backend.dto.health;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smarthealthdog.backend.domain.PetHealthCertificate;

/**
 * 건강검진표(진단서) 등록·조회·수정 응답에 공통으로 쓰이는 결과 객체.
 *
 * 견주 성명·주소는 추출하지도 저장하지도 않으므로 이 DTO 에도 존재하지 않는다.
 */
public record HealthCertificateResult(
    @JsonProperty("submission_id") UUID submissionId,
    @JsonProperty("pet_id") Long petId,
    @JsonProperty("animal_name") String animalName,
    @JsonProperty("species_text") String speciesText,
    @JsonProperty("breed") String breed,
    @JsonProperty("gender_text") String genderText,
    @JsonProperty("coat_color") String coatColor,
    @JsonProperty("age_text") String ageText,
    @JsonProperty("features") String features,
    @JsonProperty("disease_name") String diseaseName,
    @JsonProperty("onset_date") LocalDate onsetDate,
    @JsonProperty("onset_date_text") String onsetDateText,
    @JsonProperty("diagnosed_date") LocalDate diagnosedDate,
    @JsonProperty("diagnosed_date_text") String diagnosedDateText,
    @JsonProperty("prognosis") String prognosis,
    @JsonProperty("remarks") String remarks,
    @JsonProperty("ocr_confidence") BigDecimal ocrConfidence,
    @JsonProperty("is_manually_edited") boolean manuallyEdited,
    @JsonProperty("submitted_at") Instant submittedAt
) {

    /**
     * 엔티티를 응답 객체로 변환한다.
     * @param certificate 진단서 엔티티 (제출·반려동물 연관이 초기화되어 있어야 한다)
     * @return 응답 객체
     */
    public static HealthCertificateResult from(PetHealthCertificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("진단서가 null일 수 없습니다.");
        }

        return new HealthCertificateResult(
            certificate.getSubmission().getId(),
            certificate.getPet().getId(),
            certificate.getAnimalName(),
            certificate.getSpeciesText(),
            certificate.getBreed(),
            certificate.getGenderText(),
            certificate.getCoatColor(),
            certificate.getAgeText(),
            certificate.getFeatures(),
            certificate.getDiseaseName(),
            certificate.getOnsetDate(),
            certificate.getOnsetDateText(),
            certificate.getDiagnosedDate(),
            certificate.getDiagnosedDateText(),
            certificate.getPrognosis(),
            certificate.getRemarks(),
            certificate.getOcrConfidence(),
            certificate.isManuallyEdited(),
            certificate.getSubmission().getSubmittedAt()
        );
    }
}
