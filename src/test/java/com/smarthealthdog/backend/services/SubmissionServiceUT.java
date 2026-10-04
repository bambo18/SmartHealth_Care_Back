package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.ConditionTranslation;
import com.smarthealthdog.backend.domain.Diagnosis;
import com.smarthealthdog.backend.domain.Language;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.diagnosis.get.SubmissionMapper;
import com.smarthealthdog.backend.dto.diagnosis.update.DiagnosisResultDto;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.dto.health.ImageUrlResponse;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.LanguageRepository;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class SubmissionServiceUT {

    @InjectMocks
    private SubmissionService submissionService;

    @Mock
    private LanguageRepository languageRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ConditionService conditionService;

    @Mock
    private DiagnosisService diagnosisService;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private ImgUtils imgUtils;

    @Test
    void getSecureImageUrl_서명URL과_만료초를_반환한다() {
        ReflectionTestUtils.setField(submissionService, "imageUrlExpirationSeconds", 300);

        UUID id = UUID.randomUUID();
        Submission submission = mock(Submission.class);
        when(submission.getPhotoUrl()).thenReturn("health-certificates/abc.jpg");
        when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 7L))
            .thenReturn(Optional.of(submission));
        when(imgUtils.getSecureImgUrl("health-certificates/abc.jpg", Duration.ofSeconds(300)))
            .thenReturn("https://s3.example.com/health-certificates/abc.jpg?X-Amz-Expires=300");

        ImageUrlResponse response = submissionService.getSecureImageUrl(id, 7L);

        assertEquals("https://s3.example.com/health-certificates/abc.jpg?X-Amz-Expires=300",
                     response.imageUrl());
        assertEquals(300L, response.expiresIn());
    }

    @Test
    void getSecureImageUrl_이미지가_아직_없는_행이면_404를_낸다() {
        // 검토 집중 지점 1: createSubmission 은 photoUrl="" 로 행을 만든다.
        // 업로드 전/실패 행에 이 엔드포인트를 호출하면 null 서명 URL 이 나가면 안 된다.
        ReflectionTestUtils.setField(submissionService, "imageUrlExpirationSeconds", 300);

        UUID id = UUID.randomUUID();
        Submission submission = mock(Submission.class);
        when(submission.getPhotoUrl()).thenReturn("");
        when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 7L))
            .thenReturn(Optional.of(submission));

        ResourceNotFoundException e = assertThrows(
            ResourceNotFoundException.class,
            () -> submissionService.getSecureImageUrl(id, 7L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
        verify(imgUtils, never()).getSecureImgUrl(any(), any());
    }

    @Test
    void getSecureImageUrl_삭제된_제출이면_404를_낸다() {
        ReflectionTestUtils.setField(submissionService, "imageUrlExpirationSeconds", 300);

        UUID id = UUID.randomUUID();
        Submission submission = mock(Submission.class);
        when(submission.getStatus()).thenReturn(SubmissionStatus.DELETED);
        when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 7L))
            .thenReturn(Optional.of(submission));

        assertThrows(
            ResourceNotFoundException.class,
            () -> submissionService.getSecureImageUrl(id, 7L)
        );

        verify(imgUtils, never()).getSecureImgUrl(any(), any());
    }

    @Test
    void getSecureImageUrl_타인의_제출이면_404를_낸다() {
        // findByIdWithPetAndUserAndOwnerId 가 userId 조건을 포함하므로 빈 Optional 이 온다.
        UUID id = UUID.randomUUID();
        when(submissionRepository.findByIdWithPetAndUserAndOwnerId(id, 99L))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> submissionService.getSecureImageUrl(id, 99L)
        );
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenSubmissionStatusIsNotProcessing() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.COMPLETED);
        when(submissionRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(mockSubmission));

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(UUID.randomUUID(), null);
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenRequestIsNull() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.PROCESSING);
        when(submissionRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(mockSubmission));

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(UUID.randomUUID(), null);
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenResultsAreEmpty() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.PROCESSING);
        when(submissionRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(mockSubmission));

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(UUID.randomUUID(), new SubmissionResultRequest());
        });
    }

    @Test
    void completeDiagnosis_ShouldThrowInvalidRequestDataException_WhenResultsAreNull() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.PROCESSING);
        when(submissionRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(mockSubmission));

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(null);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.completeEyeTest(UUID.randomUUID(), request);
        });
    }

    @Test
    void completeDiagnosis_ShouldProcessSuccessfully_WhenValidRequest() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.PROCESSING);
        when(submissionRepository.findById(any(UUID.class))).thenReturn(java.util.Optional.of(mockSubmission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(mockSubmission);

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(List.of(new DiagnosisResultDto()));

        submissionService.completeEyeTest(UUID.randomUUID(), request);
        verify(submissionRepository).save(mockSubmission);
    }

    @Test
    void deleteSubmissionById_ShouldThrowResourceNotFoundException_WhenSubmissionIsAlreadyDeleted() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.DELETED);
        when(submissionRepository.findByIdWithPetAndUserAndOwnerId(any(UUID.class), any(Long.class)))
            .thenReturn(Optional.of(mockSubmission));

        assertThrows(ResourceNotFoundException.class, () -> {
            submissionService.deleteSubmissionById(UUID.randomUUID(), 1L);
        });
    }

    @Test
    void deleteSubmissionById_ShouldProcessSuccessfully_WhenValidSubmission() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.PROCESSING);
        when(submissionRepository.findByIdWithPetAndUserAndOwnerId(any(UUID.class), any(Long.class)))
            .thenReturn(Optional.of(mockSubmission));
        when(submissionRepository.save(any(Submission.class))).thenReturn(mockSubmission);

        submissionService.deleteSubmissionById(UUID.randomUUID(), 1L);
        verify(submissionRepository).save(mockSubmission);
    }

    @Test
    void failSubmission_ShouldThrowInvalidRequestDataException_WhenSubmissionStatusIsNotProcessing() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.COMPLETED);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.failSubmission(mockSubmission, SubmissionFailureReasonEnum.SERVICE_ERROR);
        });
    }

    @Test
    void failSubmission_ShouldProcessSuccessfully_WhenValidSubmission() {
        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getStatus()).thenReturn(SubmissionStatus.PROCESSING);
        when(submissionRepository.save(any(Submission.class))).thenReturn(mockSubmission);

        submissionService.failSubmission(mockSubmission, SubmissionFailureReasonEnum.SERVICE_ERROR);
        verify(submissionRepository).save(mockSubmission);
    }

    @Test
    void getSubmissionAndDiagnosesById_ShouldThrowInternalServerErrorException_WhenLanguageNotFound() {
        UUID submissionId = mock(UUID.class);
        when(languageRepository.findByCode("en")).thenReturn(Optional.empty());

        assertThrows(InternalServerErrorException.class, () -> {
            submissionService.getSubmissionAndDiagnosesById(submissionId, "en", 1L);
        });
    }

    @Test
    void getSubmissionAndDiagnosesById_ShouldThrowResourceNotFoundException_WhenSubmissionNotFound() {
        UUID submissionId = mock(UUID.class);
        when(languageRepository.findByCode("en")).thenReturn(Optional.of(mock(Language.class)));
        when(submissionRepository.findByIdWithPetAndUser(submissionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            submissionService.getSubmissionAndDiagnosesById(submissionId, "en", 1L);
        });
    }

    @Test
    void getSubmissionAndDiagnosesById_ShouldThrowResourceNotFoundException_WhenUserIdDoesNotMatch() {
        UUID submissionId = mock(UUID.class);
        Language mockLanguage = mock(Language.class);
        when(languageRepository.findByCode("en")).thenReturn(Optional.of(mockLanguage));

        Submission mockSubmission = mock(Submission.class);
        when(mockSubmission.getPet()).thenReturn(mock(Pet.class));
        when(mockSubmission.getPet().getOwner()).thenReturn(mock(User.class));
        when(mockSubmission.getPet().getOwner().getId()).thenReturn(2L); // Different user ID

        when(submissionRepository.findByIdWithPetAndUser(submissionId)).thenReturn(Optional.of(mockSubmission));

        assertThrows(ResourceNotFoundException.class, () -> {
            submissionService.getSubmissionAndDiagnosesById(submissionId, "en", 1L);
        });
    }

    @Test
    void getSubmissionAndDiagnosesById_ShouldThrowInternalServerErrorException_WhenDiagnosesAreEmpty() {
        UUID submissionId = mock(UUID.class);
        Language mockLanguage = mock(Language.class);
        when(languageRepository.findByCode("en")).thenReturn(Optional.of(mockLanguage));

        Submission mockSubmission = mock(Submission.class);
        Pet mockPet = mock(Pet.class);
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(1L);
        when(mockPet.getOwner()).thenReturn(mockUser);
        when(mockSubmission.getPet()).thenReturn(mockPet);

        when(submissionRepository.findByIdWithPetAndUser(submissionId)).thenReturn(Optional.of(mockSubmission));
        when(diagnosisService.getDiagnosesBySubmissionId(submissionId)).thenReturn(List.of()); // Empty diagnoses

        assertThrows(InternalServerErrorException.class, () -> {
            submissionService.getSubmissionAndDiagnosesById(submissionId, "en", 1L);
        });
    }

    @Test
    void getSubmissionAndDiagnosesById_ShouldThrowInternalServerErrorException_WhenTranslationsAreEmpty() {
        UUID submissionId = mock(UUID.class);
        Language mockLanguage = mock(Language.class);
        when(languageRepository.findByCode("en")).thenReturn(Optional.of(mockLanguage));

        Submission mockSubmission = mock(Submission.class);
        Pet mockPet = mock(Pet.class);
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(1L);
        when(mockPet.getOwner()).thenReturn(mockUser);
        when(mockSubmission.getPet()).thenReturn(mockPet);

        when(submissionRepository.findByIdWithPetAndUser(submissionId)).thenReturn(Optional.of(mockSubmission));
        Condition mockCondition = mock(Condition.class);
        when(mockCondition.getId()).thenReturn(1);
        Diagnosis mockDiagnosis = mock(Diagnosis.class);
        when(mockDiagnosis.getCondition()).thenReturn(mockCondition);

        when(diagnosisService.getDiagnosesBySubmissionId(submissionId)).thenReturn(List.of(mockDiagnosis));
        when(conditionService.getConditionTranslationsByConditionIdsAndLanguage(
            List.of(1), mockLanguage)).thenReturn(List.of()); // Empty translations

        assertThrows(InternalServerErrorException.class, () -> {
            submissionService.getSubmissionAndDiagnosesById(submissionId, "en", 1L);
        });
    }

    @Test
    void getSubmissionAndDiagnosesById_ShouldProcessSuccessfully_WhenValidInput() {
        UUID submissionId = mock(UUID.class);
        Language mockLanguage = mock(Language.class);
        when(languageRepository.findByCode("en")).thenReturn(Optional.of(mockLanguage));

        Submission mockSubmission = mock(Submission.class);
        Pet mockPet = mock(Pet.class);
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(1L);
        when(mockPet.getOwner()).thenReturn(mockUser);
        when(mockSubmission.getPet()).thenReturn(mockPet);

        when(submissionRepository.findByIdWithPetAndUser(submissionId)).thenReturn(Optional.of(mockSubmission));
        Condition mockCondition = mock(Condition.class);
        when(mockCondition.getId()).thenReturn(1);
        Diagnosis mockDiagnosis = mock(Diagnosis.class);
        when(mockDiagnosis.getCondition()).thenReturn(mockCondition);

        when(diagnosisService.getDiagnosesBySubmissionId(submissionId)).thenReturn(List.of(mockDiagnosis));
        when(conditionService.getConditionTranslationsByConditionIdsAndLanguage(
            List.of(1), mockLanguage)).thenReturn(List.of(mock(ConditionTranslation.class)));

        submissionService.getSubmissionAndDiagnosesById(submissionId, "en", 1L);
        verify(submissionMapper).toSubmissionDetailForEyeTest(
            any(Submission.class), any(List.class), any(List.class)
        );
    }

    @Test
    void getSubmissionsByPetId_ShouldThrowInvalidRequestDataException_WhenPageableIsBiggerThanMaxPageSize() {
        Long petId = 1L;
        Long userId = 2L; // Different user ID

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(16);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.getSubmissionsByPetId(
                petId, 
                userId, 
                null,
                null,
                null,
                null,
                null,
                mockPageable
            );
        });
    }

    @Test
    void getSubmissionsByPetId_ShouldThrowInvalidRequestDataException_WhenPageableHasInvalidSortProperty() {
        Long petId = 1L;
        Long userId = 1L;

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(15);
        when(mockPageable.getSort()).thenReturn(Sort.by("invalid_property"));

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.getSubmissionsByPetId(
                petId, 
                userId, 
                null,
                null,
                null,
                null,
                null,
                mockPageable
            );
        });
    }

    @Test
    void getSubmissionsByPetId_ShouldThrowResourceNotFoundException_WhenPetDoesNotBelongToUser() {
        Long petId = 1L;
        Long userId = 1L;

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(15);
        when(mockPageable.getSort()).thenReturn(Sort.by("submittedAt"));

        when(submissionRepository.existsByPetIdAndPetOwnerId(petId, userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> {
            submissionService.getSubmissionsByPetId(
                petId, 
                userId, 
                null,
                null,
                null,
                null,
                null,
                mockPageable
            );
        });
    }

    @Test
    void getSubmissionsByPetId_ShouldProcessSuccessfully_WhenValidInput() {
        Long petId = 1L;
        Long userId = 1L;

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(15);
        when(mockPageable.getSort()).thenReturn(Sort.by("submittedAt"));

        when(submissionRepository.existsByPetIdAndPetOwnerId(petId, userId)).thenReturn(true);
        Page<Submission> mockPage = mock(Page.class);
        when(submissionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        submissionService.getSubmissionsByPetId(petId, userId, null, null, null, null, null, mockPageable);
        verify(submissionMapper).toSubmissionPage(any(org.springframework.data.domain.Page.class));
    }

    @Test
    void getSubmissionsByUserId_ShouldThrowInvalidRequestDataException_WhenPageableIsBiggerThanMaxPageSize() {
        Long userId = 2L;

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(16);

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.getSubmissionsByUserId(userId, null, null, null, null, null, mockPageable);
        });
    }

    @Test
    void getSubmissionsByUserId_ShouldThrowInvalidRequestDataException_WhenPageableHasInvalidSortProperty() {
        Long userId = 1L;

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(15);
        when(mockPageable.getSort()).thenReturn(Sort.by("invalid_property"));

        assertThrows(InvalidRequestDataException.class, () -> {
            submissionService.getSubmissionsByUserId(userId, null, null, null, null, null, mockPageable);
        });
    }

    @Test
    void getSubmissionsByUserId_ShouldProcessSuccessfully_WhenValidInput() {
        Long userId = 1L;

        Pageable mockPageable = mock(Pageable.class);
        when(mockPageable.getPageSize()).thenReturn(15);
        when(mockPageable.getSort()).thenReturn(Sort.by("submittedAt"));

        Page<Submission> mockPage = mock(Page.class);
        when(submissionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        submissionService.getSubmissionsByUserId(userId, null, null, null, null, null, mockPageable);
        verify(submissionMapper).toSubmissionPage(any(org.springframework.data.domain.Page.class));
    }
}