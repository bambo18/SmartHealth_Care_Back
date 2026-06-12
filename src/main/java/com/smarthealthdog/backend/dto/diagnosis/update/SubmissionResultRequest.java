package com.smarthealthdog.backend.dto.diagnosis.update;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class SubmissionResultRequest {
    @NotEmpty
    private List<DiagnosisResultDto> results;
}
