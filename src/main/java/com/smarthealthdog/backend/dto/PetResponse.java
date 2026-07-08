// entity -> response 변환용
// controller가 바로 PetResponse를 반환하면 프론트엔드에서 사용하기 쉬워짐

package com.smarthealthdog.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.utils.ImgUtils;

// 반려동물 정보를 응답으로 보낼 때 사용되는 DTO
// 서버 -> 클라이언트
public record PetResponse(
        Long id,
        String name,
        PetSpecies species,
        String breed,
        PetGender sex,
        LocalDate birthDate,
        Boolean neutered,
        BigDecimal weightKg,
        Long ownerId,

        /**
         * 기존 PetResponse에는 펫 이미지 필드가 없어서
         * 프론트가 펫 프로필 이미지를 표시할 수 없었음.
         *
         * DB에는 S3 key가 저장되어 있고,
         * API 응답에서는 ImgUtils를 통해 프론트 접근 가능한 URL로 변환해서 내려줌.
         */
        String profilePicture
) {

    /**
     * 기존 코드에서 PetResponse.from(pet)을 쓰는 곳이 남아있을 수 있으므로
     * 삭제하지 않음.
     *
     * 이 경우 profilePicture는 null로 내려감.
     */
    public static PetResponse from(Pet pet) {
        return from(pet, null);
    }

    /**
     * pet.getProfileImage()에 저장된 S3 key를
     * 프론트 접근 가능한 URL로 변환.
     */
    public static PetResponse from(Pet pet, ImgUtils imgUtils) {
        String profilePictureUrl = null;

        if (imgUtils != null
                && pet.getProfileImage() != null
                && !pet.getProfileImage().isBlank()) {
            profilePictureUrl = imgUtils.getImgUrl(pet.getProfileImage());
        }

        return new PetResponse(
                pet.getId(),
                pet.getName(),
                pet.getSpecies(),
                pet.getBreed(),
                pet.getGender(),
                pet.getBirthday(),
                pet.getIsNeutered(),
                pet.getWeightKg(),
                pet.getOwner().getId(),
                profilePictureUrl
        );
    }
}