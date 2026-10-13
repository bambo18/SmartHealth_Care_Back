package com.smarthealthdog.backend.services;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateFieldExtractor;
import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.dto.health.ImageUrlResponse;
import com.smarthealthdog.backend.dto.health.UpdateHealthCertificateRequest;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 건강검진표(진단서) OCR 등록·조회·수정 서비스.
 *
 * 핵심 정책은 "OCR 인식에 실패하면 업로드 자체가 성립하지 않는다"이다. 검증과 인식 판정이
 * 모두 끝난 뒤에야 S3 저장과 DB 기록이 일어나므로, 실패 경로에는 롤백할 대상이 아예 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCertificateService {

    private final PetService petService;
    private final SubmissionService submissionService;
    private final FileUploadService fileUploadService;
    private final DiagnosisAttemptLimiter diagnosisAttemptLimiter;
    private final HealthCertificateOcrClient healthCertificateOcrClient;
    private final HealthCertificateFieldExtractor fieldExtractor;
    private final PetHealthCertificateRepository petHealthCertificateRepository;
    private final ImageUploader imageUploader;
    private final ImgUtils imgUtils;

    /**
     * 외부 HTTP 호출을 트랜잭션 밖에 두기 위해 저장 구간만 명시적으로 묶는다.
     * 진입 메서드에 @Transactional 을 붙이면 OCR 응답을 기다리는 동안(최대 15초)
     * DB 커넥션을 점유해 커넥션 풀이 빠르게 고갈된다.
     */
    private final TransactionTemplate transactionTemplate;

    /** S3 key 접두사. 접근 정책과 수명 주기 규칙을 따로 걸 수 있도록 기존 diagnoses/ 와 섞지 않는다. */
    private static final String IMAGE_KEY_PREFIX = "health-certificates/";

    @Value("${ocr.certificate.min-confidence}")
    private double minConfidence;

    @Value("${health-certificate.image.presigned-url-expiration-seconds}")
    private long presignedUrlExpirationSeconds;

    /**
     * 진단서 사진을 OCR 로 읽어 건강검진 기록으로 등록합니다.
     *
     * 인식에 실패하면 DB 행도 S3 객체도 만들지 않습니다.
     *
     * @param imageFile 진단서 사진
     * @param petId 반려동물 ID
     * @param ownerId 소유자 ID
     * @return 등록된 진단서 내용
     * @throws ResourceNotFoundException 반려동물이 없거나 타인의 반려동물인 경우
     * @throws InvalidRequestDataException 요청이 너무 잦거나(REQUEST_TOO_FREQUENT),
     *         이미지가 유효하지 않거나(INVALID_IMAGE), 진단서 양식이 아니거나
     *         (NOT_A_HEALTH_CERTIFICATE), 신뢰도가 기준에 못 미치거나
     *         (OCR_RECOGNITION_FAILED), 필수 항목을 찾지 못한 경우(OCR_REQUIRED_FIELD_MISSING)
     * @throws InternalServerErrorException 저장 중 오류가 발생한 경우
     */
    public HealthCertificateResult register(MultipartFile imageFile, Long petId, Long ownerId) {
        if (petId == null || ownerId == null) {
            throw new IllegalArgumentException("Pet ID and Owner ID must not be null for certificate registration.");
        }

        // 1. 소유권 검증. 타인의 반려동물 존재 여부를 노출하지 않기 위해 403 이 아니라 404 다.
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 2. 빈도 제한. 요청 1건이 유료 OCR 호출 1건을 만들므로 외부 호출보다 먼저 와야 한다.
        //    인식 성공 여부와 무관하게 "시도"가 먼저 기록된다 — 그래야 실패 요청도 제한에 걸린다.
        diagnosisAttemptLimiter.checkAndRecordAttempt(ownerId, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        // 3. 이미지 검증 (빈 파일 / 확장자 / Tika MIME / 용량)
        fileUploadService.validateImageFile(imageFile);

        byte[] fileBytes;
        try {
            fileBytes = imageFile.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }

        // 4. 외부 OCR 호출
        HealthCertificateOcrResult ocrResult =
            healthCertificateOcrClient.extract(fileBytes, imageFile.getContentType());

        // 5. 인식 규칙 평가
        PetHealthCertificate certificate = evaluate(ocrResult);

        // ── 여기까지 DB·S3 에 어떤 쓰기도 일어나지 않았다 ──

        // 6. S3 저장
        String photoKey;
        try {
            photoKey = imageUploader.storeSubmissionImage(
                new SubmissionImageUploadEvent(null, fileBytes, imageFile.getOriginalFilename(), imageFile.getContentType()),
                IMAGE_KEY_PREFIX
            );
        } catch (InvalidRequestDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("진단서 이미지 저장에 실패했습니다: petId={}", petId, e);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 7. 제출과 진단서를 한 트랜잭션에 기록. 실패하면 방금 올린 S3 객체를 보상 삭제한다.
        try {
            return transactionTemplate.execute(status -> {
                Submission submission = submissionService.createCompletedSubmission(
                    pet, SubmissionTypeEnum.HEALTH_CERTIFICATE, photoKey
                );

                certificate.setPet(pet);
                certificate.setSubmission(submission);

                return HealthCertificateResult.from(petHealthCertificateRepository.save(certificate));
            });
        } catch (Exception e) {
            log.error("진단서 기록 저장에 실패했습니다: petId={}, photoKey={}", petId, photoKey, e);
            compensateImage(photoKey);
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 등록된 진단서의 원본 이미지에 접근할 수 있는 단기 서명 URL 을 반환합니다.
     *
     * getImgUrl 은 prod 에서 CloudFront 무서명 URL 을 돌려줄 수 있으므로 쓰지 않습니다.
     *
     * @param submissionId 제출 ID
     * @param userId 사용자 ID
     * @return 서명 URL 과 만료 시간(초)
     * @throws ResourceNotFoundException 제출이 없거나 타인의 제출인 경우
     */
    @Transactional(readOnly = true)
    public ImageUrlResponse getImageUrl(UUID submissionId, Long userId) {
        Submission submission = submissionService.getSubmissionByIdAndOwnerId(submissionId, userId);

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (submission.getPhotoUrl() == null || submission.getPhotoUrl().isBlank()) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String imageUrl = imgUtils.getSecureImgUrl(
            submission.getPhotoUrl(),
            Duration.ofSeconds(presignedUrlExpirationSeconds)
        );

        return new ImageUrlResponse(imageUrl, presignedUrlExpirationSeconds);
    }

    /**
     * OCR 오인식을 보정합니다. 전달된 필드만 반영합니다.
     *
     * 한 번이라도 보정되면 수동 수정 표시가 켜지며 되돌릴 수 없습니다.
     *
     * @param submissionId 제출 ID
     * @param userId 사용자 ID
     * @param request 수정 요청 (null 필드는 변경 없음)
     * @return 수정된 진단서 내용
     * @throws ResourceNotFoundException 제출이 없거나 타인의 제출이거나 진단서가 없는 경우
     * @throws InvalidRequestDataException 필수 항목을 빈 값으로 지우려는 경우(INVALID_INPUT)
     */
    @Transactional
    public HealthCertificateResult update(UUID submissionId, Long userId, UpdateHealthCertificateRequest request) {
        if (request == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        Submission submission = submissionService.getSubmissionByIdAndOwnerId(submissionId, userId);

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        PetHealthCertificate certificate = petHealthCertificateRepository
            .findBySubmissionIdWithPet(submissionId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND));

        // 필수 항목은 지울 수 없다. null 은 "변경 없음"이고 빈 문자열은 "지우기 시도"다.
        rejectBlankRequiredField(request.getDiseaseName());
        rejectBlankRequiredField(request.getDiagnosedDateText());

        applyIfPresent(request.getAnimalName(), certificate::setAnimalName);
        applyIfPresent(request.getSpeciesText(), certificate::setSpeciesText);
        applyIfPresent(request.getBreed(), certificate::setBreed);
        applyIfPresent(request.getGenderText(), certificate::setGenderText);
        applyIfPresent(request.getCoatColor(), certificate::setCoatColor);
        applyIfPresent(request.getAgeText(), certificate::setAgeText);
        applyIfPresent(request.getFeatures(), certificate::setFeatures);
        applyIfPresent(request.getDiseaseName(), certificate::setDiseaseName);
        applyIfPresent(request.getPrognosis(), certificate::setPrognosis);
        applyIfPresent(request.getRemarks(), certificate::setRemarks);

        // 날짜는 원문을 받아 서버가 재파싱해 DATE 컬럼을 갱신한다.
        if (request.getOnsetDateText() != null) {
            certificate.setOnsetDateText(request.getOnsetDateText());
            certificate.setOnsetDate(fieldExtractor.parseDate(request.getOnsetDateText()));
        }

        if (request.getDiagnosedDateText() != null) {
            certificate.setDiagnosedDateText(request.getDiagnosedDateText());
            certificate.setDiagnosedDate(fieldExtractor.parseDate(request.getDiagnosedDateText()));
        }

        certificate.setManuallyEdited(true);

        return HealthCertificateResult.from(petHealthCertificateRepository.save(certificate));
    }

    /**
     * 인식 규칙(문서 종류 / 필수 항목 / 신뢰도)을 평가하고 통과한 진단서를 돌려준다.
     *
     * 이 메서드가 저장 단계보다 앞에 있다는 사실이 "인식 실패 시 업로드 불가"의 전부다.
     */
    private PetHealthCertificate evaluate(HealthCertificateOcrResult ocrResult) {
        if (!fieldExtractor.looksLikeHealthCertificate(ocrResult.fullText())) {
            throw new InvalidRequestDataException(ErrorCode.NOT_A_HEALTH_CERTIFICATE);
        }

        if (ocrResult.confidence() < minConfidence) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        PetHealthCertificate certificate = fieldExtractor.extract(ocrResult);

        // 병명과 진단 연원일(원문)만 필수다. 나머지 10개 항목은 비어도 등록을 허용하고
        // 수정 화면에서 사용자가 채운다.
        if (isBlank(certificate.getDiseaseName()) || isBlank(certificate.getDiagnosedDateText())) {
            throw new InvalidRequestDataException(ErrorCode.OCR_REQUIRED_FIELD_MISSING);
        }

        return certificate;
    }

    /**
     * 보상 삭제. 이마저 실패하면 객체 키를 남기고 요청은 실패로 끝낸다.
     * 고아 객체 정리 배치는 후속 과제다.
     */
    private void compensateImage(String photoKey) {
        try {
            imageUploader.delete(photoKey);
        } catch (Exception e) {
            log.error("고아 S3 객체가 남았습니다. 수동 정리가 필요합니다: key={}", photoKey, e);
        }
    }

    private void rejectBlankRequiredField(String value) {
        if (value != null && value.isBlank()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }
    }

    private void applyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
