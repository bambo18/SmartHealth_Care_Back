package com.smarthealthdog.backend.dto.respiration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateRespirationUploadRequest(
        @NotNull @Min(1) @Max(209715200) Long fileSizeBytes,
        @NotNull @Min(30) @Max(120) Integer durationSec
) {}
