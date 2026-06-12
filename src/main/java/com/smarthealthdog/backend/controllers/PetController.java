package com.smarthealthdog.backend.controllers;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.dto.CreatePetRequest;
import com.smarthealthdog.backend.dto.PartialUpdatePetRequest;
import com.smarthealthdog.backend.dto.PetResponse;
import com.smarthealthdog.backend.dto.UpdatePetRequest;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.services.PetService;
import com.smarthealthdog.backend.services.AIDiagnosisClientService;
import com.smarthealthdog.backend.validation.ErrorCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {
    private final PetService petService;
    private final AIDiagnosisClientService aiDiagnosisClientService;

    /** 반려동물 등록 */
    @PostMapping
    @PreAuthorize("hasAuthority('can_add_pet')")
    public ResponseEntity<PetResponse> create(
            @RequestPart("request") @Valid CreatePetRequest req, 
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture
    ) throws IOException {

        Long ownerId = Long.parseLong(userDetails.getUsername());
        Pet saved = petService.create(ownerId, req, profilePicture);
        return ResponseEntity.ok(PetResponse.from(saved));
    }

    /** 단건 조회 (명세서: { status, pet } 구조) */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('can_view_own_pet_detail')")
    public ResponseEntity<PetResponse> get(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        Pet pet = petService.get(id);
        if (pet.getOwner().getId() != ownerId) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return ResponseEntity.ok(PetResponse.from(pet));
    }

    /** 소유자 기준 목록 조회 */
    @GetMapping
    @PreAuthorize("hasAuthority('can_view_own_pets')")
    public ResponseEntity<List<PetResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        List<PetResponse> pets = petService.listByOwner(ownerId)
                .stream()
                .map(PetResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pets);
    }

    /** 전체 수정 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('can_edit_pet')")
    public ResponseEntity<PetResponse> update(
            @PathVariable Long id,
            @RequestPart("request") @Valid UpdatePetRequest req,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            @AuthenticationPrincipal UserDetails userDetails
    ) throws IOException {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        Pet updated = petService.update(id, ownerId, req, profilePicture);
        return ResponseEntity.ok(PetResponse.from(updated));
    }

    /** 삭제 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('can_delete_pet')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        petService.delete(id, Long.parseLong(userDetails.getUsername()));
        return ResponseEntity.noContent().build();
    }

    /** 부분 수정 (Partial Update) */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('can_edit_pet')")
    public ResponseEntity<PetResponse> partialUpdate(
            @PathVariable Long id,
            @RequestPart("request") PartialUpdatePetRequest updates,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture,
            @AuthenticationPrincipal UserDetails userDetails
    ) throws IOException {

        Pet updatedPet = petService.partialUpdate(id, updates, Long.parseLong(userDetails.getUsername()), profilePicture);
        return ResponseEntity.ok(PetResponse.from(updatedPet));
    }


    @PostMapping("/{id}/submissions/eye")
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<Void> addEyeDiagnosis(
            @PathVariable Long id,
            @RequestPart(value = "image") MultipartFile image,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        aiDiagnosisClientService.performEyeDiagnosis(image, id, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/submissions/urine")
    @PreAuthorize("hasAuthority('can_use_health_check')")
    public ResponseEntity<Void> addUrineDiagnosis(
            @PathVariable Long id,
            @RequestPart(value = "image") MultipartFile image,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long ownerId = Long.parseLong(userDetails.getUsername());
        aiDiagnosisClientService.performUrineDiagnosis(image, id, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}