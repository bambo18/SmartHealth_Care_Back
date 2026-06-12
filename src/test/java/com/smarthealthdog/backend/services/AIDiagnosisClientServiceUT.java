package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
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
        when(submissionService.getMostRecentSubmissionByPet(mockPet))
            .thenThrow(new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND)); // Simulate no recent submission

        // No exception should be thrown
        aiDiagnosisClientService.performEyeDiagnosis(null, 1L, 1L);

        // Verify that the submissionService was called
        verify(submissionService).createSubmission(mockPet, SubmissionTypeEnum.EYE);
        verify(fileUploadService).updateDiagnosisImage(any(), any());
    }
}
