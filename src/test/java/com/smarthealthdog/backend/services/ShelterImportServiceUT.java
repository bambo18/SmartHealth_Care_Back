package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.smarthealthdog.backend.domain.Shelter;
import com.smarthealthdog.backend.dto.shelter.ShelterImportRequest.ShelterRecord;
import com.smarthealthdog.backend.repositories.ShelterRepository;

class ShelterImportServiceUT {

    @Test
    void importShelters_createsNewAndUpdatesExisting() {
        ShelterRepository repository = mock(ShelterRepository.class);
        Shelter existing = new Shelter();
        existing.setName("Existing Shelter");
        when(repository.findAllByNameIn(List.of("Existing Shelter", "New Shelter")))
                .thenReturn(List.of(existing));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ShelterService service = new ShelterService(repository);

        var response = service.importShelters(List.of(
                shelterRecord("Existing Shelter", "Updated address"),
                shelterRecord("New Shelter", "New address")
        ));

        assertEquals(1, response.created());
        assertEquals(1, response.updated());
        assertEquals(2, response.total());
        assertEquals("Updated address", existing.getAddress());
        verify(repository).saveAll(anyList());
    }

    private ShelterRecord shelterRecord(String name, String address) {
        return new ShelterRecord(
                name, null, address, null, null, null,
                null, null, null, null, false, null, "test", null
        );
    }

}