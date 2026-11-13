package com.smarthealthdog.backend.dto.respiration;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** AI contract: respiratoryRate must be a positive integer BPM. */
public record RespirationWorkerResultRequest(
    @NotNull @DecimalMin("1") @Digits(integer = 4, fraction = 0) BigDecimal respiratoryRate
) {}
