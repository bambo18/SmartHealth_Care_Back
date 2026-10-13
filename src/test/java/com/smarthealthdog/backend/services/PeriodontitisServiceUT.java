package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smarthealthdog.backend.domain.OralHealthRecord;
import com.smarthealthdog.backend.domain.PeriodontalStage;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.TartarLevel;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.periodontitis.PeriodontitisRecordRequest;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.OralHealthRecordRepository;

@ExtendWith(MockitoExtension.class)
class PeriodontitisServiceUT {

    @InjectMocks
    private PeriodontitisService periodontitisService;

    @Mock
    private OralHealthRecordRepository oralHealthRecordRepository;

    @Mock
    private PetService petService;

    @Test
    void createRecord_savesRecordForPetOwner() {
        Pet pet = ownedPet(7L, 3L);
        PeriodontitisRecordRequest request = new PeriodontitisRecordRequest(
                PeriodontalStage.STAGE2,
                TartarLevel.MEDIUM,
                true,
                false,
                1,
                "Gum inflammation"
        );
        when(petService.get(7L)).thenReturn(pet);
        when(oralHealthRecordRepository.save(org.mockito.ArgumentMatchers.any(OralHealthRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = periodontitisService.createRecord(7L, 3L, request);

        assertEquals(7L, response.petId());
        assertEquals(PeriodontalStage.STAGE2, response.periodontalStage());
        verify(oralHealthRecordRepository).save(org.mockito.ArgumentMatchers.any(OralHealthRecord.class));
    }

    @Test
    void getRecords_rejectsPetOwnedByAnotherUser() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(9L);
        Pet pet = mock(Pet.class);
        when(pet.getOwner()).thenReturn(owner);
        when(petService.get(7L)).thenReturn(pet);

        assertThrows(ResourceNotFoundException.class,
                () -> periodontitisService.getRecords(7L, 3L));
    }

    @Test
    void getRecords_returnsNewestRecordsFromRepository() {
        Pet pet = ownedPet(7L, 3L);
        OralHealthRecord record = OralHealthRecord.builder()
                .pet(pet)
                .periodontalStage(PeriodontalStage.GINGIVITIS)
                .toothLossCount(0)
                .build();
        when(petService.get(7L)).thenReturn(pet);
        when(oralHealthRecordRepository.findByPetIdOrderByRecordedAtDesc(7L))
                .thenReturn(List.of(record));

        var response = periodontitisService.getRecords(7L, 3L);

        assertEquals(1, response.size());
        assertEquals(PeriodontalStage.GINGIVITIS, response.get(0).periodontalStage());
    }

    private Pet ownedPet(Long petId, Long ownerId) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        Pet pet = mock(Pet.class);
        when(pet.getId()).thenReturn(petId);
        when(pet.getOwner()).thenReturn(owner);
        return pet;
    }
}