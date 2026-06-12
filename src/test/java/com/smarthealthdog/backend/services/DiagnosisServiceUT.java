package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.dto.diagnosis.update.DiagnosisResultDto;
import com.smarthealthdog.backend.dto.diagnosis.update.SubmissionResultRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisRepository;

@ExtendWith(MockitoExtension.class)
public class DiagnosisServiceUT {
    @InjectMocks
    private DiagnosisService diagnosisService;

    @Mock
    private ConditionService conditionService;

    @Mock
    private DiagnosisRepository diagnosisRepository;

    @Test
    void processInferenceResult_ShouldThrowIllegalArgumentException_WhenNoValidDiagnoses() {
        DiagnosisResultDto resultDto = new DiagnosisResultDto();
        resultDto.setDisease("SomeDisease");
        resultDto.setProbability(new BigDecimal("0.85"));
        resultDto.setModelMd5Hash("dummyhash");

        Pet pet = mock(Pet.class);
        when(pet.getSpecies()).thenReturn(PetSpecies.DOG);

        Submission submission = mock(Submission.class);
        when(submission.getPet()).thenReturn(pet);

        when(conditionService.getConditionsBySpecies(PetSpecies.DOG)).thenReturn(List.of());

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(List.of(resultDto));

        assertThrows(InvalidRequestDataException.class, () -> {
            diagnosisService.processInferenceResult(submission, request);
        });
    }

    @Test
    void processInferenceResult_ShouldProcessSuccessfully_WhenValidDiagnosesExist() {
        DiagnosisResultDto resultDto = new DiagnosisResultDto();
        resultDto.setDisease("SomeDisease");
        resultDto.setProbability(new BigDecimal("0.85"));
        resultDto.setModelMd5Hash("dummyhash");

        Pet pet = mock(Pet.class);
        when(pet.getSpecies()).thenReturn(PetSpecies.DOG);

        Submission submission = mock(Submission.class);
        when(submission.getPet()).thenReturn(pet);

        Condition condition = mock(Condition.class);
        when(condition.getName()).thenReturn("SomeDisease");

        when(conditionService.getConditionsBySpecies(PetSpecies.DOG)).thenReturn(List.of(condition));

        SubmissionResultRequest request = new SubmissionResultRequest();
        request.setResults(List.of(resultDto));

        diagnosisService.processInferenceResult(submission, request);
        verify(diagnosisRepository).saveAll(any());
    }
}
