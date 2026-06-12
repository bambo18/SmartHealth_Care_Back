//entity -> response 변환용
//controller가 바로 petresponse를 반환하면 프론트엔드에서 사용하기 쉬워짐

package com.smarthealthdog.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;

//반려동물 정보를 응답으로 보낼 때 사용되는 dto
//서버->클라이언트
public record PetResponse(
        Long id,
        String name,
        PetSpecies species,
        String breed,
        PetGender sex,
        LocalDate birthDate,
        Boolean neutered,
        BigDecimal weightKg,
        Long ownerId
) {
    public static PetResponse from(Pet pet) {
        return new PetResponse(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getGender(),
                pet.getBirthday(),
                pet.getIsNeutered(),
                pet.getWeightKg(),
                pet.getOwner().getId()
        );
    }
}