package com.smarthealthdog.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSocialKakaoUserRequest(
    @NotBlank
    String accessToken
) {}
