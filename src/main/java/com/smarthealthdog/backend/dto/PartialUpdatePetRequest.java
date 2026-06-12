package com.smarthealthdog.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record PartialUpdatePetRequest(
        @Size(min=1, max=255) String name,
        PetSpecies species,
        @Size(max=255) String breed,
        PetGender gender,
        @PastOrPresent LocalDate birthDate,
        Boolean neutered,
        @DecimalMin("0.0") @DecimalMax("9999.9") BigDecimal weightKg
) {}