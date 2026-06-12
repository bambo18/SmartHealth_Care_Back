package com.smarthealthdog.backend.dto.diagnosis.update;

import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class SubmissionStatusUpdateRequest {
    @NotNull
    SubmissionStatus status;
    SubmissionFailureReasonEnum failureReason;
}
