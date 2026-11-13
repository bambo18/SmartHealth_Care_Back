package com.smarthealthdog.backend.dto.respiration;

import com.smarthealthdog.backend.domain.RespirationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Only COMPLETED or FAILED is accepted by the service. */
public record RespirationWorkerStatusRequest(
    @NotNull RespirationStatus status,
    @Size(max = 256) String failureReason
) {}
