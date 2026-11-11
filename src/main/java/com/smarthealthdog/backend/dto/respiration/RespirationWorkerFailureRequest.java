package com.smarthealthdog.backend.dto.respiration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespirationWorkerFailureRequest(
    @NotBlank @Size(max = 256) String failureReason
) {}
