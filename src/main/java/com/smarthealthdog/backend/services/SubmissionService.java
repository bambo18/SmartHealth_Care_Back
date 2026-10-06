package com.smarthealthdog.backend.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;
import com.smarthealthdog.backend.domain.ConditionTranslation;
import com.smarthealthdog.backend.domain.Diagnosis;
import com.smarthealthdog.backend.domain.Language;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.UrineMeasurement;
import com.smarthealthdog.backend.dto.diagnosis.get.DiagnosisResult;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionDetail;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionMapper;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionPage;
import com.smarthealthdog.backend.dto.diagnosis.get.UrineMeasurementResult;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionStatusUpdateRequest;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionUrineTestUpdateRequest;
import com.smarthealthdog.backend.dto.health.HealthCertificateResult;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.LanguageRepository;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.repositories.SubmissionSpecifications;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final LanguageRepository languageRepository;
    private final SubmissionRepository submissionRepository;
    private final ConditionService conditionService;
    private final DiagnosisService diagnosisService;
    private final UrineMeasurementService urineMeasurementService;
    private final PetHealthCertificateRepository petHealthCertificateRepository;
    private final SubmissionMapper submissionMapper;

    private static final int MAX_PAGE_SIZE = 15;
    private static final List<String> ALLOWED_SORT_PROPERTIES = List.of("submittedAt", "completedAt", "status");

    /**
     * 제출된 안구 질환 진단 결과를 처리하고 제출 상태를 완료로 업데이트합니다. 
     * @param submissionId 제출 ID
     * @param request 제출 결과 요청 객체
     * @return 업데이트된 제출 정보
     */
    @Transactional
    public Submission completeEyeTest(UUID submissionId, SubmissionResultRequest request) {
        Submission submission = getSubmissionById(submissionId);
        if (submission.getStatus() != SubmissionStatus.PROCESSING) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        if (request == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        if (request.getResults() == null || request.getResults().isEmpty()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        // 1. 진단 결과 처리
        diagnosisService.processInferenceResult(submission, request);

        // 2. 제출 상태 업데이트
        submission.setStatus(SubmissionStatus.COMPLETED); 
        submission.setCompletedAt(Instant.now());
        
        // 3. 제출 정보 저장
        return submissionRepository.save(submission);
    }

    /**
     * 제출된 소변 검사 결과를 처리하고 제출 상태를 완료로 업데이트합니다.
     * @param submissionId 제출 ID
     * @param request 소변 검사 결과 요청 객체
     */
    @Transactional
    public void completeUrineTest(UUID submissionId, SubmissionUrineTestUpdateRequest request) { 
        Submission submission = getSubmissionById(submissionId);
        if (submission.getStatus() != SubmissionStatus.PROCESSING) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        if (request == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        if (request.getResults() == null || request.getResults().isEmpty()) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        // 1. 소변 검사 결과 처리
        urineMeasurementService.processUrineMeasurements(submission, request);

        // 2. 제출 상태 업데이트
        submission.setStatus(SubmissionStatus.COMPLETED); 
        submission.setCompletedAt(Instant.now());
        
        // 3. 제출 정보 저장
        submissionRepository.save(submission);
    }

    /**
     * 새로운 제출 정보를 생성합니다. (저장 전)
     * @param pet 반려동물 정보
     * @param type 제출 유형
     * @return 생성된 제출 정보
     */
    public Submission createSubmission(Pet pet, SubmissionTypeEnum type) {
        Instant now = Instant.now();

        return Submission.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .type(type)
                .pet(pet)
                .photoUrl("") // 실제 URL은 S3 업로드 후 설정됩니다.
                .submittedAt(now)
                .build();
    }

    /**
     * 이미 완료된 상태의 제출 정보를 생성하고 저장합니다. (동기 처리 경로 전용)
     *
     * 동기 경로는 외부 추론이 끝난 뒤에야 기록을 만들기 때문에 처음부터 COMPLETED 입니다.
     * 그래서 PENDING 만 집는 PushAIInferenceTasks, PROCESSING 만 집는 RetryAIInferenceTasks
     * 어느 쪽도 이 행을 건드리지 않습니다 — Celery 큐로 흘러갈 경로가 구조적으로 없습니다.
     *
     * @param pet 반려동물 정보
     * @param type 제출 유형
     * @param photoKey 저장된 이미지의 S3 object key
     * @return 저장된 제출 정보
     */
    @Transactional
    public Submission createCompletedSubmission(Pet pet, SubmissionTypeEnum type, String photoKey) {
        if (pet == null || type == null) {
            throw new IllegalArgumentException("Pet and submission type must not be null.");
        }

        if (photoKey == null || photoKey.isBlank()) {
            throw new IllegalArgumentException("Photo key must not be blank.");
        }

        Instant now = Instant.now();

        Submission submission = Submission.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .type(type)
                .pet(pet)
                .photoUrl(photoKey)
                .status(SubmissionStatus.COMPLETED)
                .submittedAt(now)
                .completedAt(now)
                .build();

        return submissionRepository.save(submission);
    }

    /**
     * 제출 ID로 제출 정보와 건강검진표(진단서) 결과를 함께 가져옵니다.
     * @param id 제출 ID
     * @param userId 사용자 ID
     * @return 제출 정보 (진단서 결과 포함)
     * @throws ResourceNotFoundException 제출이 없거나 타인의 제출인 경우
     * @throws ResourceNotFoundException 제출에 연결된 진단서가 없는 경우
     */
    @Transactional(readOnly = true)
    public SubmissionDetail<HealthCertificateResult> getSubmissionAndCertificateById(UUID id, Long userId) {
        if (id == null) {
            throw new IllegalArgumentException("Submission ID must not be null");
        }

        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        // 소유권 검증이 내장된 조회. 타인의 리소스는 404 로 끝난다.
        Submission submission = getSubmissionByIdAndOwnerId(id, userId);

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        PetHealthCertificate certificate = petHealthCertificateRepository.findBySubmissionIdWithPet(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND));

        return submissionMapper.toSubmissionDetailForCertificate(submission, certificate);
    }

    /**
     * 제출을 삭제 상태로 업데이트합니다.
     * @param submissionId 제출 ID
     * @param userId 사용자 ID
     */
    @Transactional
    public void deleteSubmissionById(UUID submissionId, Long userId) {
        Submission submission = getSubmissionByIdAndOwnerId(submissionId, userId);
        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        submission.setStatus(SubmissionStatus.DELETED);
        submissionRepository.save(submission);
    }

    /**
     * 제출을 실패 상태로 업데이트합니다.
     * @param submissionId 제출 ID
     * @param failureReason 실패 사유
     * @return 업데이트된 제출 정보
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Submission failSubmission(Submission submission, SubmissionFailureReasonEnum failureReason) {
        if (submission.getStatus() != SubmissionStatus.PROCESSING) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }

        submission.setStatus(SubmissionStatus.FAILED);
        submission.setFailureReason(failureReason);
        submission.setCompletedAt(Instant.now());

        return submissionRepository.save(submission);
    }

    /**
     * 반려동물의 가장 최근 제출 정보를 가져옵니다.
     * @param pet 반려동물 정보
     * @return 가장 최근 제출 정보
     */
    public Submission getMostRecentSubmissionByPet(Pet pet) {
        return submissionRepository.findTopByPetOrderBySubmittedAtDesc(pet)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 반려동물의 같은 유형 중 가장 최근 제출 정보를 가져옵니다.
     *
     * 빈도 제한은 같은 유형끼리만 비교해야 합니다 — 유형을 가리지 않으면 진단서를 올린 직후
     * 눈 진단이 거부되는 교차 간섭이 생깁니다.
     *
     * @param pet 반려동물 정보
     * @param type 제출 유형
     * @return 가장 최근 제출 정보 (없으면 비어 있음)
     */
    public Optional<Submission> getMostRecentSubmissionByPetAndType(Pet pet, SubmissionTypeEnum type) {
        return submissionRepository.findTopByPetAndTypeOrderBySubmittedAtDesc(pet, type);
    }

    /**
     * 진단 ID로 제출 정보를 가져옵니다.
     * @param id 진단 제출 ID
     * @return 제출 정보
     */
    public Submission getSubmissionById(UUID id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 진단 ID와 소유자 ID로 제출 정보를 가져옵니다.
     * @param id 진단 제출 ID
     * @param userId 사용자 ID
     * @return 제출 정보
     */
    public Submission getSubmissionByIdAndOwnerId(UUID id, Long userId) {
        return submissionRepository.findByIdWithPetAndUserAndOwnerId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 진단 ID로 제출 정보와 관련된 안구 질환 진단 및 번역 정보를 함께 가져옵니다.
     * @param id 진단 제출 ID
     * @param languageCode 선호 언어 코드
     * @param userId 사용자 ID
     * @return 제출 정보 (진단 및 번역 포함)
     * @throws IllegalArgumentException 잘못된 인수 예외
     * @throws ResourceNotFoundException 리소스 없음 예외
     * @throws InternalServerErrorException 내부 서버 오류 예외
     */
    public SubmissionDetail<DiagnosisResult> getSubmissionAndDiagnosesById(UUID id, String languageCode, Long userId) {
        if (id == null) {
            throw new IllegalArgumentException("Submission ID must not be null");
        }

        if (languageCode == null) {
            languageCode = "ko"; // Default to Korean if not provided
        }

        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        Language preferredLanguage = languageRepository.findByCode(languageCode).
            orElseThrow(() -> new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR));

        // 1. 서브미션 로드 및 소유자 검증
        Submission submission = submissionRepository.findByIdWithPetAndUser(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!submission.getPet().getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 2. 진단 로드
        List<Diagnosis> diagnoses = diagnosisService.getDiagnosesBySubmissionId(id);
        if (diagnoses.isEmpty()) {
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 3. 진단에서 조건 ID 추출
        List<Integer> conditionIds = diagnoses.stream()
            .map(d -> d.getCondition().getId())
            .distinct()
            .collect(Collectors.toList());

        // 4. 조건 번역 로드
        List<ConditionTranslation> translations = conditionService.getConditionTranslationsByConditionIdsAndLanguage(
            conditionIds, 
            preferredLanguage
        );
        if (translations.isEmpty()) {
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return submissionMapper.toSubmissionDetailForEyeTest(submission, diagnoses, translations);
    }

    public SubmissionDetail<UrineMeasurementResult> getSubmissionAndUrineMeasurementsById(
        UUID id, 
        String languageCode, 
        Long userId
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Submission ID must not be null");
        }

        if (languageCode == null) {
            languageCode = "ko"; // Default to Korean if not provided
        }

        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        // 1. 서브미션 로드 및 소유자 검증
        Submission submission = submissionRepository.findByIdWithPetAndUser(id)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (submission.getStatus() == SubmissionStatus.DELETED) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!submission.getPet().getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 2. 소변 검사 결과 로드
        List<UrineMeasurement> measurements = urineMeasurementService.getUrineMeasurementsForSubmission(submission);
        if (measurements.isEmpty()) {
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return submissionMapper.toSubmissionDetailForUrineTest(submission, measurements);
    }

    /**
     * 반려동물 ID로 제출 페이지를 가져옵니다.
     * @param petId 반려동물 ID
     * @param userId 사용자 ID
     * @param submittedFrom 제출일 시작 범위
     * @param submittedTo 제출일 종료 범위
     * @param completedFrom 완료일 시작 범위
     * @param completedTo 완료일 종료 범위
     * @param type 제출 유형 필터 (null 가능)
     * @param pageable 페이지 정보
     * @return 제출 페이지
     * @throws InvalidRequestDataException 잘못된 요청 데이터 예외
     */
    public SubmissionPage getSubmissionsByPetId(
        Long petId, 
        Long userId, 
        Instant submittedFrom, 
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo,
        SubmissionTypeEnum type,
        Pageable pageable
    ) {
        if (pageable == null) {
            pageable = PageRequest.of(0, 15);
        }

        // 페이지 크기가 15를 초과하지 않고 0보다 큰지 확인
        if (pageable.getPageSize() > MAX_PAGE_SIZE || pageable.getPageSize() <= 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_PAGE_SIZE);
        }

        // Pageable에서 Sort 값 유효성 검사
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_SORT_PROPERTY);
            }
        });

        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_DATE_RANGE);
        }

        if (completedFrom != null && completedTo != null && completedFrom.isAfter(completedTo)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_DATE_RANGE);
        }

        if (!submissionRepository.existsByPetIdAndPetOwnerId(petId, userId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Page<Submission> page = submissionRepository.findAll(
            SubmissionSpecifications.filterPetSubmissions(
                userId,
                petId,
                submittedFrom,
                submittedTo,
                completedFrom,
                completedTo,
                type
            ),
            pageable
        );

        return submissionMapper.toSubmissionPage(page);
    }

    /**
     * 사용자 ID로 제출 페이지를 가져옵니다.
     * @param userId 사용자 ID
     * @param submittedFrom 제출일 시작 범위
     * @param submittedTo 제출일 종료 범위
     * @param completedFrom 완료일 시작 범위
     * @param completedTo 완료일 종료 범위
     * @param type 제출 유형 필터 (null 가능)
     * @param pageable 페이지 정보
     * @return 제출 페이지
     * @throws InvalidRequestDataException 잘못된 요청 데이터 예외
     */
    public SubmissionPage getSubmissionsByUserId(
        Long userId, 
        Instant submittedFrom,
        Instant submittedTo,
        Instant completedFrom,
        Instant completedTo,
        SubmissionTypeEnum type,
        Pageable pageable
    ) {
        if (pageable == null) {
            pageable = PageRequest.of(0, 15);
        }

        // 페이지 크기가 15를 초과하지 않고 0보다 큰지 확인
        if (pageable.getPageSize() > MAX_PAGE_SIZE || pageable.getPageSize() <= 0) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_PAGE_SIZE);
        }

        // Pageable에서 Sort 값 유효성 검사
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_SORT_PROPERTY);
            }
        });

        if (submittedFrom != null && submittedTo != null && submittedFrom.isAfter(submittedTo)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_DATE_RANGE);
        }

        if (completedFrom != null && completedTo != null && completedFrom.isAfter(completedTo)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_DATE_RANGE);
        }

        Page<Submission> page = submissionRepository.findAll(
            SubmissionSpecifications.filterUserSubmissions(
                userId,
                submittedFrom,
                submittedTo,
                completedFrom,
                completedTo,
                type
            ),
            pageable
        );

        return submissionMapper.toSubmissionPage(page);
    }

    /**
     * 제출 정보를 저장합니다.
     * @param submission 진단 제출 객체
     * @return 저장된 제출 정보
     */
    @Transactional
    public Submission saveSubmission(Submission submission) {
        return submissionRepository.save(submission);
    }

    /**
     * AI 추론 후 제출 상태를 업데이트합니다. 준비 상태, 실패 상태로 전환할 수 있습니다.
     * @param submissionId 제출 ID
     * @param statusUpdateRequest 상태 업데이트 요청
     */
    @Transactional
    public void updateSubmissionStatusAfterInference(UUID submissionId, SubmissionStatusUpdateRequest statusUpdateRequest) {
        Submission submission = getSubmissionById(submissionId);

        // 현재 상태가 PROCESSING, COMPLETED, DELETED인 경우 상태 업데이트 불가
// 이미 같은 상태면 그냥 성공
	if (submission.getStatus() == statusUpdateRequest.getStatus()) {
   	    return;
	}

// COMPLETED, DELETED는 다른 상태로 변경 불가
	if (
    	    submission.getStatus() == SubmissionStatus.COMPLETED ||
   	    submission.getStatus() == SubmissionStatus.DELETED
	) {
   	    throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
	}

        submission.setStatus(statusUpdateRequest.getStatus());
        if (statusUpdateRequest.getStatus() == SubmissionStatus.FAILED) {
            submission.setFailureReason(statusUpdateRequest.getFailureReason());
            submission.setCompletedAt(Instant.now());
        } else if (
            statusUpdateRequest.getStatus() == SubmissionStatus.PENDING
        ) {
            submission.setCompletedAt(null);
            submission.setFailureReason(null);
        }

        submissionRepository.save(submission);
    }
}
