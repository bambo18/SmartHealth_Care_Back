package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 진단서 기록 저장 전용 빈.
 *
 * 별도 빈으로 분리한 이유는 Spring 프록시 때문이다 —
 * HealthCertificateService 는 외부 HTTP 호출을 트랜잭션 밖에 두어야 해서
 * 비트랜잭션이고, 같은 클래스 안에서 this.persist(...) 를 호출하면
 * 프록시를 거치지 않아 @Transactional 이 무시된다.
 */
@Service
@RequiredArgsConstructor
public class HealthCertificateWriter {

    private final SubmissionService submissionService;
    private final PetHealthCertificateRepository petHealthCertificateRepository;

    /**
     * 제출과 진단서 기록을 한 트랜잭션에서 저장한다.
     *
     * createCompletedSubmission 도 @Transactional 이지만 기본 전파(REQUIRED)라
     * 이 트랜잭션에 참여한다 — 둘 중 하나가 실패하면 함께 롤백된다.
     *
     * @param pet 반려동물
     * @param photoKey S3 업로드가 끝난 object key
     * @param ocr 판별을 통과한 OCR 결과
     * @return 저장된 진단서 기록
     * @throws InvalidRequestDataException 같은 제출에 진단서가 이미 있는 경우 {@code INVALID_INPUT}
     */
    @Transactional
    public PetHealthCertificate persist(Pet pet, String photoKey, HealthCertificateOcrResult ocr) {
        Submission submission = submissionService.createCompletedSubmission(
            pet,
            SubmissionTypeEnum.HEALTH_CERTIFICATE,
            photoKey
        );

        // uq_pet_health_certificates_submission 위반이 500 으로 노출되기 전에 막는다.
        // 새로 만든 제출이라 정상 흐름에서는 걸리지 않지만, 재시도 경로에서 걸릴 수 있다.
        if (petHealthCertificateRepository.existsBySubmissionId(submission.getId())) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        PetHealthCertificate certificate = PetHealthCertificate.builder()
            .pet(pet)
            .submission(submission)
            .animalName(ocr.animalName())
            .speciesText(ocr.speciesText())
            .breed(ocr.breed())
            .genderText(ocr.genderText())
            .coatColor(ocr.coatColor())
            .ageText(ocr.ageText())
            .features(ocr.features())
            .diseaseName(ocr.diseaseName())
            .onsetDate(ocr.onsetDate())
            .onsetDateText(ocr.onsetDateText())
            .diagnosedDate(ocr.diagnosedDate())
            .diagnosedDateText(ocr.diagnosedDateText())
            .prognosis(ocr.prognosis())
            .remarks(ocr.remarks())
            // NUMERIC(5,4) 이므로 소수 4자리로 맞춘다. 반올림하지 않으면 저장 시 잘린다.
            .ocrConfidence(BigDecimal.valueOf(ocr.averageConfidence())
                                     .setScale(4, RoundingMode.HALF_UP))
            .manuallyEdited(false)
            .build();

        return petHealthCertificateRepository.save(certificate);
    }
}
