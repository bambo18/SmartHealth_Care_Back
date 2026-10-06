package com.smarthealthdog.backend.services;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AIDiagnosisClientService {
    private final FileUploadService fileUploadService;
    private final PetService petService;
    private final SubmissionService submissionService;

    @Value("${inference-service.interval.seconds}")
    private int inferenceIntervalSeconds;

    /**
     * 눈 질병 진단을 수행합니다. (개발 및 테스트 환경용)
     * @param imageFile 이미지 파일
     * @param petId 반려동물 ID
     * @param ownerId 소유자 ID
     * @throws IllegalArgumentException petId 또는 ownerId가 null인 경우
     * @throws ResourceNotFoundException 반려동물을 찾을 수 없는 경우
     * @throws InvalidRequestDataException 이미지 업로드에 실패한 경우
     */
    @Transactional
    public void performEyeDiagnosis(MultipartFile imageFile, Long petId, Long ownerId) {
        if (petId == null || ownerId == null) {
            throw new IllegalArgumentException("Pet ID and Owner ID must not be null for eye diagnosis.");
        }

        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        checkSubmissionInterval(pet, SubmissionTypeEnum.EYE);

        Submission submissionBuild = submissionService.createSubmission(pet, SubmissionTypeEnum.EYE);
        Submission submission = submissionService.saveSubmission(submissionBuild);

        // 진단 이미지 업로드
        fileUploadService.updateDiagnosisImage(submission, imageFile);
    }

    /**
     * 소변 검사 진단을 수행합니다. (개발 및 테스트 환경용)
     * @param imageFile 이미지 파일
     * @param petId 반려동물 ID
     * @param ownerId 소유자 ID
     * @throws IllegalArgumentException petId 또는 ownerId가 null인 경우
     * @throws ResourceNotFoundException 반려동물을 찾을 수 없는 경우
     * @throws InvalidRequestDataException 이미지 업로드에 실패한 경우
     */
    @Transactional
    public void performUrineDiagnosis(MultipartFile imageFile, Long petId, Long ownerId) {
        if (petId == null || ownerId == null) {
            throw new IllegalArgumentException("Pet ID and Owner ID must not be null for urine diagnosis.");
        }

        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        checkSubmissionInterval(pet, SubmissionTypeEnum.URINE);

        Submission submissionBuild = submissionService.createSubmission(pet, SubmissionTypeEnum.URINE);
        Submission submission = submissionService.saveSubmission(submissionBuild);

        // 진단 이미지 업로드
        fileUploadService.updateDiagnosisImage(submission, imageFile);
    }

    /**
     * 같은 유형의 가장 최근 제출을 기준으로 요청 간격을 검사합니다.
     *
     * 유형을 가리지 않고 검사하면 진단서를 올린 직후 눈 진단이 거부되는 교차 간섭이 생깁니다.
     *
     * @param pet 반려동물 정보
     * @param type 제출 유형
     * @throws InvalidRequestDataException 요청 간격이 너무 짧은 경우
     */
    private void checkSubmissionInterval(Pet pet, SubmissionTypeEnum type) {
        submissionService.getMostRecentSubmissionByPetAndType(pet, type)
            .ifPresent(recentSubmission -> {
                long secondsSinceLastSubmission =
                    Duration.between(recentSubmission.getSubmittedAt(), Instant.now()).getSeconds();

                if (secondsSinceLastSubmission < inferenceIntervalSeconds) {
                    throw new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT);
                }
            });
    }
}
