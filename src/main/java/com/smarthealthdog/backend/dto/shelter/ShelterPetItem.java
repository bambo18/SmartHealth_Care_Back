package com.smarthealthdog.backend.dto.shelter;

public record ShelterPetItem(
        Long pet_id,
        String name,
        String species,
        String breed,
        String gender,
        String age,
        Boolean is_neutered,
        String image_url,
        String adoption_status
) {}