package com.smarthealthdog.backend.dto.diagnosis.update;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DiagnosisResultDto {
    @NotBlank
    private String disease;

    @NotNull // Field must be present
    @DecimalMin("0.0") // Value must be >= 0.0
    @DecimalMax("1.0") // Value must be <= 1.0 (for a probability)
    private BigDecimal probability; // Change to BigDecimal!

    @NotBlank
    private String modelMd5Hash;
}
