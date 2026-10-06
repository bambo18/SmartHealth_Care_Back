package com.smarthealthdog.backend.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateOcrClient;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.health.ocr.HealthCertificateOcrResult;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.utils.FileUtils;
import com.smarthealthdog.backend.utils.ImageUploader;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 진단서 등록 동기 흐름.
 *
 * 클래스에 @Transactional 을 붙이지 않는다 —
 * OCR 응답을 최대 15초 기다리는 동안 DB 커넥션을 점유하면
 * 커넥션 풀이 빠르게 고갈된다. 저장만 HealthCertificateWriter 에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCertificateService {

    private static final String S3_PREFIX = "health-certificates/";

    private final PetService petService;
    private final DiagnosisAttemptLimiter diagnosisAttemptLimiter;
    private final FileUploadService fileUploadService;
    private final HealthCertificateOcrClient ocrClient;
    private final ImageUploader imageUploader;
    private final HealthCertificateWriter healthCertificateWriter;

    @Value("${ocr.certificate.min-confidence}")
    private double minConfidence;

    /**
     * 진단서 이미지를 OCR 로 읽어 건강검진 기록을 만든다.
     *
     * 판별에 실패하면 DB 행도 S3 객체도 만들지 않는다 —
     * OCR 호출과 판별이 저장보다 앞에 있으므로 롤백할 대상이 애초에 없다.
     *
     * @param image 진단서 이미지
     * @param petId 반려동물 ID
     * @param ownerId 요청자(소유자) 내부 ID
     * @return 저장된 진단서 기록
     * @throws ResourceNotFoundException 반려동물이 없거나 타인 소유인 경우 {@code RESOURCE_NOT_FOUND}
     * @throws InvalidRequestDataException 빈도 제한 {@code REQUEST_TOO_FREQUENT},
     *         이미지 위반 {@code INVALID_IMAGE}, 진단서 아님 {@code NOT_A_HEALTH_CERTIFICATE},
     *         필수 항목 누락 {@code OCR_REQUIRED_FIELD_MISSING},
     *         신뢰도 미달 {@code OCR_RECOGNITION_FAILED}
     * @throws IllegalArgumentException petId 또는 ownerId 가 null 인 경우
     */
    public PetHealthCertificate register(MultipartFile image, Long petId, Long ownerId) {
        if (petId == null || ownerId == null) {
            throw new IllegalArgumentException("Pet ID 와 Owner ID 는 null 일 수 없습니다.");
        }

        // 2. 소유권 — 타인의 반려동물 존재 여부를 노출하지 않기 위해 404 (403 아님)
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 3. 빈도 제한 — 유료 OCR 호출보다 반드시 앞에 둔다.
        //    통과 시 시도가 즉시 기록되므로, 이후 판별 실패해도 다음 요청이 제한된다.
        diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        // 4. 이미지 검증 — 기존 메서드를 그대로 재사용한다.
        fileUploadService.validateImageFile(image);

        byte[] imageBytes = readBytes(image);
        String detectedMimeType = detectMimeType(imageBytes);

        // 5. 외부 OCR 호출 — 실패/타임아웃은 클라이언트가 503 으로 매핑한다.
        HealthCertificateOcrResult ocr = ocrClient.extract(imageBytes, detectedMimeType);

        // 6. 판별 규칙 — 미달이면 여기서 끝난다.
        validateOcrResult(ocr);

        // ══════ 여기까지 DB·S3 에 어떤 쓰기도 없었다 ══════

        // 7. S3 업로드
        String photoKey = imageUploader.storeSubmissionImage(
            new SubmissionImageUploadEvent(null, imageBytes, image.getOriginalFilename(), detectedMimeType),
            S3_PREFIX
        );

        // 8. 저장 — 실패 시 보상 삭제로 고아 객체를 남기지 않는다.
        try {
            return healthCertificateWriter.persist(pet, photoKey, ocr);
        } catch (RuntimeException e) {
            compensateDelete(photoKey, e);
            throw e;
        }
    }

    /**
     * 판별 규칙 (SPEC 6.1).
     *
     * 나머지 10개 항목은 비어도 등록을 허용한다 — 진단서마다 빈칸이 흔하고
     * 수정 화면에서 사용자가 채울 수 있다.
     */
    private void validateOcrResult(HealthCertificateOcrResult ocr) {
        // 1. 문서 종류 — 고유 키워드가 하나도 없으면 진단서가 아니다.
        if (!ocr.documentKeywordFound()) {
            throw new InvalidRequestDataException(ErrorCode.NOT_A_HEALTH_CERTIFICATE);
        }

        // 2. 필수 항목 — 병명과 진단 연원일 원문.
        //    날짜는 파싱값이 아니라 원문을 본다. 파싱 실패가 등록을 막지 않는다.
        if (isBlank(ocr.diseaseName()) || isBlank(ocr.diagnosedDateText())) {
            throw new InvalidRequestDataException(ErrorCode.OCR_REQUIRED_FIELD_MISSING);
        }

        // 3. 신뢰도 임계값 — 임계값과 같은 값은 통과시킨다.
        if (ocr.averageConfidence() < minConfidence) {
            throw new InvalidRequestDataException(ErrorCode.OCR_RECOGNITION_FAILED);
        }
    }

    private void compensateDelete(String photoKey, RuntimeException cause) {
        try {
            imageUploader.delete(photoKey);
        } catch (RuntimeException deleteFailure) {
            // 보상 삭제 실패는 원래 원인을 덮지 않는다.
            // 고아 객체 키를 로그에 남겨 수동 정리가 가능하게 한다.
            log.error("보상 삭제 실패 — 고아 S3 객체가 남았습니다. key={}", photoKey, deleteFailure);
        }
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
    }

    private String detectMimeType(byte[] imageBytes) {
        try {
            String mimeType = FileUtils.detectImageMimeType(new ByteArrayInputStream(imageBytes));
            if (mimeType == null) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
            }

            return mimeType;
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
