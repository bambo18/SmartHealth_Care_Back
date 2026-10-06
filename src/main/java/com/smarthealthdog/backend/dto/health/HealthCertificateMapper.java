package com.smarthealthdog.backend.dto.health;

import org.springframework.stereotype.Component;

import com.smarthealthdog.backend.domain.PetHealthCertificate;

/**
 * 진단서 엔티티 → 응답 DTO.
 *
 * SubmissionMapper 와 분리한 이유:
 * SubmissionMapper 는 ImgUtils 를 주입받아 photoUrl 을 채우는데,
 * 진단서 응답은 photoUrl 을 쓰지 않는다(전용 이미지 엔드포인트만 사용).
 * 이미지 URL 생성 의존성을 이 매퍼에 끌어오지 않는다.
 */
@Component
public class HealthCertificateMapper {

    /**
     * @throws IllegalArgumentException certificate 가 null 인 경우
     */
    public HealthCertificateResult toResult(PetHealthCertificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("진단서 기록이 null일 수 없습니다.");
        }

        return new HealthCertificateResult(
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
            certificate.isManuallyEdited()
        );
    }

    /**
     * @throws IllegalArgumentException certificate 가 null 인 경우
     */
    public HealthCertificateCreatedResponse toCreatedResponse(PetHealthCertificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("진단서 기록이 null일 수 없습니다.");
        }

        return new HealthCertificateCreatedResponse(
            certificate.getSubmission().getId(),
            certificate.getPet().getId(),
            certificate.getSubmission().getSubmittedAt(),
            toResult(certificate)
        );
    }
}
