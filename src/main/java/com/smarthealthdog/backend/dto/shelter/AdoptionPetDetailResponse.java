package com.smarthealthdog.backend.dto.shelter;

import java.time.Instant;
import java.util.List;

public record AdoptionPetDetailResponse(
        Long pet_id,
        Long shelter_id,
        String name,
        String species,          // "dog" | "cat" ...
        String breed,
        String gender,           // "male" | "female" | "unknown"
        String age,              // "2y", "8m" 등
        Boolean is_neutered,
        String adoption_status,  // AVAILABLE | PENDING | ADOPTED ...
        String description,
        List<String> images,
        Instant created_at,
        Instant updated_at,
        String share_url,
        ShelterContact shelter_contact
) {
    public record ShelterContact(
            String name,
            String phone_number,
            String address,
            String website_url
    ) {}
}