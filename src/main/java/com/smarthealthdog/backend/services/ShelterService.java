package com.smarthealthdog.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Shelter;
import com.smarthealthdog.backend.dto.shelter.AdoptionPetDetailResponse;
import com.smarthealthdog.backend.dto.shelter.AdoptionStatus;
import com.smarthealthdog.backend.dto.shelter.ShelterPetItem;
import com.smarthealthdog.backend.dto.shelter.ShelterPetsResponse;
import com.smarthealthdog.backend.dto.shelter.ShelterProfileResponse;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.ShelterRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShelterService {

    private final ShelterRepository shelterRepository;

    /** 보호소 기본 정보 조회 */
    public ShelterProfileResponse getProfile(Long shelterId) {
        Shelter s = shelterRepository.findById(shelterId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        return ShelterProfileResponse.from(s);
    }

    /** 보호소 입양 가능 동물 목록 */
    public ShelterPetsResponse listAdoptionPets(Long shelterId, String status, Integer limit, Integer offset) {
        if (limit == null || limit <= 0) limit = 20;
        if (offset == null || offset < 0) offset = 0;

        shelterRepository.findById(shelterId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        AdoptionStatus st = AdoptionStatus.fromNullable(status);

        // ⚙️ TODO: DB or 외부 API 붙이기 전, 더미로 테스트 가능
        List<ShelterPetItem> items = new ArrayList<>();
        // items.add(new ShelterPetItem(1L, "해피", "dog", "Jindo", "female", "2y", true, "https://example.com/happy.jpg", st.name()));

        return ShelterPetsResponse.of(shelterId, items, limit, offset, st.name());
    }

    //입양 가능 동물 상세 조회()
    public AdoptionPetDetailResponse getAdoptionPetDetail(Long shelterId, Long petId) {
        // 1) 보호소 존재 확인 (없으면 404)
        var shelter = shelterRepository.findById(shelterId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2) TODO: DB 또는 외부 API(Kakao/구글/내부 adopt_pets 테이블 등)에서 pet 상세를 조회
        //    - 없으면 404 던지기
        //    - 지금은 Postman 테스트 가능하도록 mock 응답을 리턴
        //    - mock은 petId를 그대로 반영해서 프론트 연결 테스트에 사용 가능
        return new AdoptionPetDetailResponse(
                petId,
                shelterId,
                "해피",
                "dog",
                "Jindo",
                "female",
                "2y",
                true,
                "AVAILABLE",
                "활발하고 사람을 잘 따르며 기본 건강검진을 마쳤습니다. 산책을 좋아해요.",
                List.of(
                        "https://cdn.example.com/adopt/" + petId + "/1.jpg",
                        "https://cdn.example.com/adopt/" + petId + "/2.jpg",
                        "https://cdn.example.com/adopt/" + petId + "/3.jpg"
                ),
                Instant.parse("2025-08-30T04:10:00Z"),
                Instant.parse("2025-09-22T11:20:00Z"),
                "https://app.example.com/adopt/" + petId,
                new AdoptionPetDetailResponse.ShelterContact(
                        shelter.getName(),
                        shelter.getPhoneNumber(),
                        shelter.getAddress(),
                        null   // website_url: 현재 컬럼 없으면 null
                )
        );
    }
}