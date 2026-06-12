package com.smarthealthdog.backend.dto;

public record UserProfile(
    Long id,
    String nickname,
    String email,
    String profilePicture
) {}
