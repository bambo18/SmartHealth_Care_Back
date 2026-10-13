package com.smarthealthdog.backend.dto.shelter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ShelterImportRequest(
        @NotEmpty @Size(max = 500) List<@Valid ShelterRecord> shelters
) {
    public record ShelterRecord(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 20) String phoneNumber,
            @Size(max = 512) String address,
            String introduction,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Size(max = 50) String province,
            @Size(max = 50) String district,
            @Size(max = 50) String facilityType,
            @Size(max = 255) String jurisdiction,
            boolean outsideDistrict,
            Boolean active,
            @Size(max = 255) String source,
            LocalDate sourceCheckedAt
    ) {}
}