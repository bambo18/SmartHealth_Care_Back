package com.smarthealthdog.backend.dto.respiration;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record RespirationWorkerResultRequest(
    @NotNull @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal respiratoryRate
) {}
