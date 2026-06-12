package com.smarthealthdog.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "유효하지 않은 이메일 형식입니다.")
    String email, 

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    String password
) {}
