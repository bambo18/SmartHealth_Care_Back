package com.smarthealthdog.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.OralHealthRecord;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.dto.periodontitis.PeriodontitisRecordRequest;
import com.smarthealthdog.backend.dto.periodontitis.PeriodontitisRecordResponse;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.OralHealthRecordRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PeriodontitisService {

    private final OralHealthRecordRepository oralHealthRecordRepository;
    private final PetService petService;

    @Transactional
    public PeriodontitisRecordResponse createRecord(
            Long petId,
            Long ownerId,
            PeriodontitisRecordRequest request
    ) {
        Pet pet = getOwnedPet(petId, ownerId);

        OralHealthRecord record = OralHealthRecord.builder()
                .pet(pet)
                .periodontalStage(request.periodontalStage())
                .tartarLevel(request.tartarLevel())
                .gingivaInflammation(request.gingivaInflammation())
                .halitosis(request.halitosis())
                .toothLossCount(request.toothLossCount())
                .note(request.note())
                .build();

        return PeriodontitisRecordResponse.from(
                oralHealthRecordRepository.save(record)
        );
    }

    @Transactional(readOnly = true)
    public List<PeriodontitisRecordResponse> getRecords(Long petId, Long ownerId) {
        getOwnedPet(petId, ownerId);

        return oralHealthRecordRepository.findByPetIdOrderByRecordedAtDesc(petId)
                .stream()
                .map(PeriodontitisRecordResponse::from)
                .toList();
    }

    private Pet getOwnedPet(Long petId, Long ownerId) {
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return pet;
    }
}