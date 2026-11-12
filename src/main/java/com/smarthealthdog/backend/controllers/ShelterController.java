package com.smarthealthdog.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthealthdog.backend.dto.shelter.ShelterPetsResponse;
import com.smarthealthdog.backend.dto.shelter.ShelterImportRequest;
import com.smarthealthdog.backend.dto.shelter.ShelterImportResponse;
import com.smarthealthdog.backend.services.ShelterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/shelters")
@RequiredArgsConstructor
public class ShelterController {

    private final ShelterService shelterService;

    /** 보호소 정보를 일괄 등록하거나 갱신합니다. */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('can_manage_all_shelters')")
    public ResponseEntity<ShelterImportResponse> importShelters(
            @Validated @RequestBody ShelterImportRequest request
    ) {
        return ResponseEntity.ok(shelterService.importShelters(request.shelters()));
    }

    /** 보호소 기본 프로필 조회 */
    @GetMapping("/{shelterId}")
    public ResponseEntity<?> getProfile(@PathVariable Long shelterId) {
        return ResponseEntity.ok(
                java.util.Map.of("shelter", shelterService.getProfile(shelterId))
        );
    }

    /** 보호소 입양 가능 동물 목록 조회 */
    @GetMapping("/{shelterId}/pets")
    public ResponseEntity<ShelterPetsResponse> listPets(
            @PathVariable Long shelterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset
    ) {
        return ResponseEntity.ok(
                shelterService.listAdoptionPets(shelterId, status, limit, offset)
        );
    }

    //입양 가능 동물 상세 조회
    @GetMapping("/{shelterId}/pets/{petId}")
    public ResponseEntity<?> getAdoptionPetDetail(
            @PathVariable Long shelterId,
            @PathVariable Long petId
    ) {
        var detail = shelterService.getAdoptionPetDetail(shelterId, petId);
        // 명세처럼 "pet": {...} 래핑
        return ResponseEntity.ok(java.util.Map.of("pet", detail));
    }
}