package com.smarthealthdog.backend.dto.users;

import com.smarthealthdog.backend.domain.User;

public record UserProfilePictureUploadEvent(
    User user,
    byte[] fileBytes,
    String originalFilename,
    String contentType
) {}
