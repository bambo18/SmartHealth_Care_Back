//기존 반려동물 수정 시 사용 ownerId는 바뀌지 않아서 포함 x
package com.smarthealthdog.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

//클라이언트->서버
//ownerId는 이미 고정된 값이라 제외
//PUT /api/pets/{id} 요청 시 Body로 들어오는 내용과 매핑
public record UpdatePetRequest(
        @NotBlank @Size(min=1, max=255) String name,
        @NotNull PetSpecies species,
        @Size(max=255) String breed,
        @NotNull PetGender gender,
        @PastOrPresent LocalDate birthDate,
        @NotNull Boolean neutered,
        @DecimalMin("0.0") @DecimalMax("9999.9") BigDecimal weightKg
) {}
