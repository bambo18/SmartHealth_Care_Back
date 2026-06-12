package com.smarthealthdog.backend.dto.diagnosis.update;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Value;

@Value
public class SubmissionUrineTestUpdateRequest {
    @NotEmpty
    List<SubmissionUrineTestResultDto> results;
}
