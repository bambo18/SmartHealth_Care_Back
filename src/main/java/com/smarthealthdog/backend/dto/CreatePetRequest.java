//클라이언트가 "반려동물 등록"할 때 사용하는 요청 dto
//@valid 검증이 적용돼서 Controller에서 자동 유효성 체크 가능
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

//클라이언트 -> 서버
//클라이언트가 /api/pets 로 POST 요청을 보낼 때 Body(JSON)와 매핑됨
//@NotBlank, @NotNull, @PastOrPresent 등으로 유효성 검증 자동 처리
public record CreatePetRequest(
        @NotBlank @Size(min=1, max=255) String name,
        @NotNull PetSpecies species,
        @Size(max=255) String breed,
        @NotNull PetGender gender,
        @PastOrPresent LocalDate birthDate,
        @NotNull Boolean neutered,
        @DecimalMin(value="0.0", inclusive=true) @DecimalMax(value="9999.9", inclusive=true) BigDecimal weightKg
) {}
