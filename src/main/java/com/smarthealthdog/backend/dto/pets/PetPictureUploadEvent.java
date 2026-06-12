package com.smarthealthdog.backend.dto.pets;

import com.smarthealthdog.backend.domain.Pet;

public record PetPictureUploadEvent(
    Pet pet,
    byte[] fileBytes,
    String originalFilename,
    String contentType
) {}
