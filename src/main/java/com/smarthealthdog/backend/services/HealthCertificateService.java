package com.smarthealthdog.backend.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.diagnosis.update.CertificateOcrResultRequest;
import com.smarthealthdog.backend.dto.health.CertificateSubmissionAccepted;
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

    /**
     * 다시 시도하면 풀릴 수 있는 워커 오류 코드.
     *
     * 나머지(UNSUPPORTED_FORMAT·IMAGE_TOO_LARGE·NO_TEXT_DETECTED·INVALID_IMAGE)는
     * 같은 이미지로 재시도해도 같은 결과가 나오므로 즉시 실패로 끝낸다.
     */
    private static final Set<String> RETRYABLE_WORKER_ERRORS =
        Set.of("OCR_TIMEOUT", "OCR_RATE_LIMIT", "OCR_SERVICE_ERROR");

    /** 운영자가 고쳐야 하는 오류. 사용자에게 원인을 노출하지 않는다. */
    private static final String OCR_AUTH_ERROR = "OCR_AUTH_ERROR";

    @Value("${ocr.certificate.min-confidence}")
    private double minConfidence;

    @Value("${inference-service.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${health-certificate.image.presigned-url-expiration-seconds}")
    private long presignedUrlExpirationSeconds;

    /**
     * 진단서 사진을 OCR 로 읽어 건강검진 기록으로 등록합니다.
     *
     * 비동기 처리입니다. 이 메서드는 제출을 PENDING 으로 만들고 이미지 업로드 이벤트만
     * 발행한 뒤 바로 반환합니다. 실제 추출은 AI 워커가 하고, 결과는
     * {@link #applyOcrResult} 로 돌아옵니다.
     *
     * 눈·소변 진단({@code AIDiagnosisClientService})과 완전히 같은 경로입니다 —
     * 이미지는 커밋 이후 비동기로 S3 에 올라가고, {@code photoUrl} 이 채워진 뒤에야
     * {@code PushAIInferenceTasks} 가 큐에 싣습니다.
     *
     * @param imageFile 진단서 사진
     * @param petId 반려동물 ID
     * @param ownerId 소유자 ID
     * @return 접수 확인 (제출 ID·상태). 추출 결과는 아직 없습니다.
     * @throws ResourceNotFoundException 반려동물이 없거나 타인의 반려동물인 경우
     * @throws InvalidRequestDataException 요청이 너무 잦거나(REQUEST_TOO_FREQUENT),
     *         이미지가 유효하지 않은 경우(INVALID_IMAGE)
     */
    @Transactional
    public CertificateSubmissionAccepted register(MultipartFile imageFile, Long petId, Long ownerId) {
        if (petId == null || ownerId == null) {
            throw new IllegalArgumentException("Pet ID and Owner ID must not be null for certificate registration.");
        }

        // 1. 소유권 검증. 타인의 반려동물 존재 여부를 노출하지 않기 위해 403 이 아니라 404 다.
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 2. 빈도 제한. 요청 1건이 유료 OCR 호출 1건을 만들므로 큐에 싣기 전에 와야 한다.
        diagnosisAttemptLimiter.checkAndRecordAttempt(ownerId, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        // 3. 제출을 PENDING 으로 먼저 만든다. photoUrl 은 아직 비어 있고, 비동기 업로드가
        //    끝나야 채워진다. PushAIInferenceTasks 는 photoUrl 이 빈 행을 거르므로
        //    업로드 완료 전에 큐로 새어 나가지 않는다.
        Submission submission = submissionService.saveSubmission(
            submissionService.createSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        // 4. 이미지 검증 후 업로드 이벤트 발행. 바이트 읽기는 이 스레드에서 끝내고
        //    (임시 파일은 요청이 끝나면 사라진다), 실제 전송은 커밋 이후 비동기로 일어난다.
        fileUploadService.updateDiagnosisImage(submission, imageFile);

        return CertificateSubmissionAccepted.from(submission);
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
     * AI 워커가 돌려준 진단서 OCR 결과를 반영합니다.
     *
     * 비동기 파이프라인의 종착점입니다. 워커는 추출만 하고, 항목 분해·인식 규칙 평가·
     * 상태 전이는 모두 여기서 일어납니다.
     *
     * <p>워커 입장에서 "정상 보고"인 한 항상 성공으로 응답합니다. 인식 규칙에 걸려
     * 제출이 실패 처리되더라도 그것은 워커의 잘못이 아니므로 4xx 를 돌려주지 않습니다 —
     * 4xx 를 주면 워커가 같은 작업을 재시도합니다.
     *
     * @param submissionId 제출 ID
     * @param request 워커가 보낸 결과
     * @throws ResourceNotFoundException 제출이 없는 경우
     * @throws InvalidRequestDataException 제출이 PROCESSING 상태가 아닌 경우(INVALID_INPUT)
     */
    @Transactional
    public void applyOcrResult(UUID submissionId, CertificateOcrResultRequest request) {
        if (request == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        Submission submission = submissionService.getSubmissionById(submissionId);

        if (submission.getType() != SubmissionTypeEnum.HEALTH_CERTIFICATE) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        // COMPLETED·DELETED 는 종착 상태다. 워커의 중복 콜백이나 지각 콜백을 여기서 막는다.
        if (submission.getStatus() != SubmissionStatus.PROCESSING) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        if (Boolean.FALSE.equals(request.getSuccess())) {
            handleWorkerFailure(submission, request);
            return;
        }

        // 워커가 신뢰도를 주지 않을 수 있다(AI 계약 미확정). 그 경우는 "품질이 낮다"가 아니라
        // "판정 근거가 없다"이므로 임계값 자체를 넣어 게이트를 통과시키고, 저장 값은 NULL 로 남긴다.
        //
        // evaluate() 의 신뢰도 게이트를 register() 동기 경로와 공유하고 있어서 쓰는 우회다.
        // 동기 경로를 제거할 때(PLAN §1.2) 게이트를 이 메서드로 옮기고 이 우회를 없앤다.
        BigDecimal reportedConfidence = request.getConfidence();
        double confidenceForGate =
            reportedConfidence == null ? minConfidence : reportedConfidence.doubleValue();

        PetHealthCertificate certificate;
        try {
            certificate = evaluate(new HealthCertificateOcrResult(request.getRawText(), confidenceForGate));
        } catch (InvalidRequestDataException e) {
            // 인식 규칙 위반은 요청 오류가 아니라 "이 제출의 결과"다.
            fail(submission, toFailureReason(e.getErrorCode()));
            return;
        }

        if (reportedConfidence == null) {
            certificate.setOcrConfidence(null);
        }

        certificate.setPet(submission.getPet());
        certificate.setSubmission(submission);
        petHealthCertificateRepository.save(certificate);

        submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setFailureReason(null);
        submission.setCompletedAt(Instant.now());
        submissionService.saveSubmission(submission);
    }

    /**
     * 워커가 보고한 실패를 처리한다.
     *
     * 일시적 오류는 PENDING 으로 되돌려 재적재되게 하고, 확정 실패는 즉시 FAILED 로 끝낸다.
     * 확정 실패를 재시도하면 같은 실패를 max-attempts 만큼 반복할 뿐이다.
     */
    private void handleWorkerFailure(Submission submission, CertificateOcrResultRequest request) {
        String code = request.getError() == null ? "" : request.getError().trim();

        if (RETRYABLE_WORKER_ERRORS.contains(code)) {
            if (submission.getRetryCount() < maxRetryAttempts) {
                submission.setRetryCount(submission.getRetryCount() + 1);
                submission.setStatus(SubmissionStatus.PENDING);
                submission.setProcessingStartedAt(null);
                submissionService.saveSubmission(submission);
                log.info("진단서 OCR 일시 오류로 재시도합니다: submissionId={}, error={}, retryCount={}",
                    submission.getId(), code, submission.getRetryCount());
                return;
            }

            fail(submission, SubmissionFailureReasonEnum.TIMEOUT);
            return;
        }

        if (OCR_AUTH_ERROR.equals(code)) {
            // 운영자 문제다. 사용자에게는 일반 오류로 보이고, 내부에는 추적 가능한 기록을 남긴다.
            log.error("진단서 OCR 인증 오류입니다. Google Cloud 자격증명을 확인해야 합니다: submissionId={}, detail={}",
                submission.getId(), request.getErrorDetail());
            fail(submission, SubmissionFailureReasonEnum.SERVICE_ERROR);
            return;
        }

        log.warn("진단서 OCR 실패를 수신했습니다: submissionId={}, error={}, detail={}",
            submission.getId(), code, request.getErrorDetail());
        fail(submission, toFailureReason(code));
    }

    /** 제출을 실패로 확정한다. */
    private void fail(Submission submission, SubmissionFailureReasonEnum reason) {
        submission.setStatus(SubmissionStatus.FAILED);
        submission.setFailureReason(reason);
        submission.setCompletedAt(Instant.now());
        submissionService.saveSubmission(submission);
    }

    /** 워커 오류 코드를 사용자에게 보여줄 실패 사유로 옮긴다. */
    private SubmissionFailureReasonEnum toFailureReason(String workerErrorCode) {
        return switch (workerErrorCode) {
            case "NO_TEXT_DETECTED" -> SubmissionFailureReasonEnum.NO_TEXT_DETECTED;
            case "INVALID_IMAGE", "UNSUPPORTED_FORMAT", "IMAGE_TOO_LARGE"
                -> SubmissionFailureReasonEnum.IMAGE_UNREADABLE;
            // 계약에 없는 코드가 오면 일반 추론 오류로 떨어뜨린다. 로그로 추적한다.
            default -> SubmissionFailureReasonEnum.INFERENCE_ERROR;
        };
    }

    /** 인식 규칙 위반 ErrorCode 를 실패 사유로 옮긴다. */
    private SubmissionFailureReasonEnum toFailureReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case NOT_A_HEALTH_CERTIFICATE -> SubmissionFailureReasonEnum.NOT_A_CERTIFICATE;
            case OCR_RECOGNITION_FAILED -> SubmissionFailureReasonEnum.LOW_CONFIDENCE;
            case OCR_REQUIRED_FIELD_MISSING -> SubmissionFailureReasonEnum.REQUIRED_FIELD_MISSING;
            default -> SubmissionFailureReasonEnum.INFERENCE_ERROR;
        };
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
