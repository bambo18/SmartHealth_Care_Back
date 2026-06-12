package com.smarthealthdog.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
    @NotBlank
    @Size(min = 3, max = 128)
    String nickname
) {}
