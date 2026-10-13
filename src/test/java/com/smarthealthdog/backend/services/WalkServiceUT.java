package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.repositories.WalkRepository;
import com.smarthealthdog.backend.utils.DateUtils;

class WalkServiceUT {

    private WalkRepository walkRepository;
    private PetService petService;
    private WalkService walkService;

    @BeforeEach
    void setUp() {
        walkRepository = mock(WalkRepository.class);
        petService = mock(PetService.class);
        walkService = new WalkService(walkRepository, petService, mock(DateUtils.class));
    }

    @Test
    void listByPet_passesRawOffsetToDatabasePageable() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(42L);
        Pet pet = mock(Pet.class);
        when(pet.getOwner()).thenReturn(owner);
        when(petService.get(7L)).thenReturn(pet);
        when(walkRepository.findByPetIdAndStartTimeRange(
                eq(7L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        walkService.listByPet(7L, 42L, null, null, "date_asc", 10, 37);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(walkRepository).findByPetIdAndStartTimeRange(
                eq(7L), isNull(), isNull(), pageableCaptor.capture());
        assertEquals(37L, pageableCaptor.getValue().getOffset());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
        assertEquals(Sort.Direction.ASC, pageableCaptor.getValue().getSort().getOrderFor("startTime").getDirection());
    }
}