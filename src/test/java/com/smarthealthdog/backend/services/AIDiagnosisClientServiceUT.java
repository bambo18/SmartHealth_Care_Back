package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class AIDiagnosisClientServiceUT {
    @InjectMocks
    private AIDiagnosisClientService aiDiagnosisClientService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private PetService petService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Test
    void performEyeDiagnosis_ShouldThrowIllegalArgumentException_WhenPetIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, null, 1L);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldThrowIllegalArgumentException_WhenOwnerIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, 1L, null);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldThrowIllegalArgumentException_WhenBothOwnerIdAndPetIdAreNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, null, null);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldThrowResourceNotFoundException_WhenPetDoesNotBelongToOwner() {
        User mockOwner = mock(User.class);    
        when(mockOwner.getId()).thenReturn(2L); // Different owner ID

        Pet mockPet = mock(Pet.class);
        when(mockPet.getOwner()).thenReturn(mockOwner);

        when(petService.get(1L)).thenReturn(mockPet);

        assertThrows(ResourceNotFoundException.class, () -> {
            aiDiagnosisClientService.performEyeDiagnosis(null, 1L, 1L);
        });
    }

    @Test
    void performEyeDiagnosis_ShouldProceed_WhenPetBelongsToOwner() {
        User mockOwner = mock(User.class);    
        when(mockOwner.getId()).thenReturn(1L); // Same owner ID

        Pet mockPet = mock(Pet.class);
        when(mockPet.getOwner()).thenReturn(mockOwner);

        when(petService.get(1L)).thenReturn(mockPet);

        // No exception should be thrown — 제한기 목은 기본적으로 아무것도 하지 않는다.
        aiDiagnosisClientService.performEyeDiagnosis(null, 1L, 1L);

        // 빈도 제한이 Submission 생성보다 먼저 호출되어야 한다 —
        // 뒤에 두면 제한이 비용과 행 생성을 막지 못한다.
        InOrder inOrder = inOrder(diagnosisAttemptLimiter, submissionService, fileUploadService);
        inOrder.verify(diagnosisAttemptLimiter).checkAndRecordAttempt(mockPet, SubmissionTypeEnum.EYE);
        inOrder.verify(submissionService).createSubmission(mockPet, SubmissionTypeEnum.EYE);
        inOrder.verify(fileUploadService).updateDiagnosisImage(any(), any());
    }

    @Test
    void performEyeDiagnosis_빈도_제한에_걸리면_Submission을_만들지_않는다() {
        User mockOwner = mock(User.class);
        when(mockOwner.getId()).thenReturn(1L);

        Pet mockPet = mock(Pet.class);
        when(mockPet.getOwner()).thenReturn(mockOwner);
        when(petService.get(1L)).thenReturn(mockPet);

        doThrow(new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT))
            .when(diagnosisAttemptLimiter)
            .checkAndRecordAttempt(mockPet, SubmissionTypeEnum.EYE);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> aiDiagnosisClientService.performEyeDiagnosis(null, 1L, 1L)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(submissionService, never()).createSubmission(any(), any());
        verify(fileUploadService, never()).updateDiagnosisImage(any(), any());
    }

    @Test
    void performUrineDiagnosis_눈_진단과_독립된_유형으로_제한을_검사한다() {
        // 5.5(a): 유형을 가리지 않던 기존 제한에서는 눈 직후 소변이 거부됐다.
        User mockOwner = mock(User.class);
        when(mockOwner.getId()).thenReturn(1L);

        Pet mockPet = mock(Pet.class);
        when(mockPet.getOwner()).thenReturn(mockOwner);
        when(petService.get(1L)).thenReturn(mockPet);

        aiDiagnosisClientService.performUrineDiagnosis(null, 1L, 1L);

        verify(diagnosisAttemptLimiter).checkAndRecordAttempt(mockPet, SubmissionTypeEnum.URINE);
        verify(diagnosisAttemptLimiter, never())
            .checkAndRecordAttempt(mockPet, SubmissionTypeEnum.EYE);
    }
}
