package com.smarthealthdog.backend.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetGender;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.CreatePetRequest;
import com.smarthealthdog.backend.dto.PartialUpdatePetRequest;
import com.smarthealthdog.backend.dto.UpdatePetRequest;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PetService {
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FileUploadService fileUploadService;

    /**
     * 반려동물 등록
     * @param userId 보호자 ID
     * @param req 등록 요청 DTO
     * @param profileImage 프로필 이미지 파일
     * @return 등록된 반려동물 엔티티
     */
    @Transactional
    public Pet create(Long userId, CreatePetRequest req, MultipartFile profileImage) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        Pet pet = Pet.builder()
                .name(req.name())
                .species(req.species())
                .breed(req.breed())
                .gender(req.gender())
                .birthday(req.birthDate())
                .isNeutered(req.neutered())
                .weightKg(req.weightKg())
                .owner(user)
                .build();

        petRepository.save(pet);

        if (profileImage != null && !profileImage.isEmpty()) {
            fileUploadService.uploadPetImage(pet, profileImage);
        }

        return pet;
    }

    /**
     * 단건 조회 (get)
     * @param id 반려동물 ID
     * @return 반려동물 엔티티
     */
    @Transactional(readOnly = true)
    public Pet get(Long id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        return pet;
    }

    /**
     * 소유자 기준 목록 조회 (list by owner)
     * @param ownerId
     * @return 반려동물 엔티티 리스트
     */
    @Transactional(readOnly = true)
    public List<Pet> listByOwner(Long ownerId) {
        return petRepository.findByOwnerId(ownerId);
    }

    /**
     * 전체 수정 (update)
     * @param id 반려동물 ID
     * @param ownerId 소유자 ID
     * @param req 수정 요청 DTO
     * @param profileImage 프로필 이미지 파일
     * @return 수정된 반려동물 엔티티
     */
    @Transactional
    public Pet update(Long id, Long ownerId, UpdatePetRequest req, MultipartFile profileImage) throws IOException {
        Pet p = get(id); // 존재 확인 + 엔티티 로드
        if (!p.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        p.update(
            req.name(),
            req.species(),
            req.breed(),
            req.gender(),
            req.birthDate(),
            req.neutered(),
            req.weightKg(),
            null
        );

        petRepository.save(p);

        if (profileImage != null && !profileImage.isEmpty()) {
            fileUploadService.uploadPetImage(p, profileImage);
        }

        return p;
    }

    /**
     * 삭제
     * @param id 반려동물 ID
     * @param ownerId 소유자 ID
     */
    @Transactional
    public void delete(Long id, Long ownerId) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        petRepository.deleteById(id);
    }

    /**
     * 부분 수정 (partial update)
     * @param id 반려동물 ID
     * @param updates 부분 수정 요청 DTO
     * @param ownerId 소유자 ID
     * @param profileImage 프로필 이미지 파일
     * @return 수정된 반려동물 엔티티
     */
    @Transactional
    public Pet partialUpdate(Long id, PartialUpdatePetRequest updates, Long ownerId, MultipartFile profileImage) throws IOException {
        boolean changed = false; // 변경사항 추적용 플래그
        Pet p = get(id); // 존재 확인 + 영속 엔티티 로드
        
        User owner = p.getOwner(); // 소유자 정보는 변경 불가

        if (!owner.getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (updates == null) {
            return p; // 변경사항 없음
        }

        // 현재 값(기본값)으로 로컬 변수 복사
        String    name     = p.getName();
        PetSpecies species = p.getSpecies();
        String    breed    = p.getBreed();
        PetGender gender   = p.getGender();
        LocalDate birth    = p.getBirthday();
        Boolean   neutered = p.getIsNeutered();
        BigDecimal weightKg = p.getWeightKg();
        String    profileImageKey = p.getProfileImage();

        // 전달된 필드만 반영 (명세서의 snake_case도 허용)
        if (updates.name() != null) { 
            name = updates.name();
            changed = true;
        }
        if (updates.species() != null) {
            species = updates.species();
            changed = true;
        }
        if (updates.breed() != null) {
            breed = updates.breed();
            changed = true;
        }

        if (updates.gender() != null) {
            gender = updates.gender();
            changed = true;
        }

        if (updates.birthDate() != null) {
            birth = updates.birthDate();
            changed = true;
        }

        if (updates.neutered() != null) {
            neutered = updates.neutered();
            changed = true;
        }

        if (updates.weightKg() != null) {
            weightKg = updates.weightKg();
            changed = true;
        }

        // 우리 엔티티는 필드별 setter 대신 update(전체) 메서드로 반영
        if (changed) {
            p.update(name, species, breed, gender, birth, neutered, weightKg, profileImageKey);
            petRepository.save(p);
        }

        if (profileImage != null) {
            // 프로필 이미지 처리 로직 추가 (예: 파일 저장, URL 업데이트 등)
            if (!profileImage.isEmpty()) {
                fileUploadService.uploadPetImage(p, profileImage);
            } else {
                // 빈 파일이 전달된 경우, 기존 이미지를 삭제 처리
                p.setProfileImage(null);
                petRepository.save(p);
            }
        }

        // JPA 변경감지로 DB 업데이트
        return p;
    }
}