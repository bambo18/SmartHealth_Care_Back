package com.smarthealthdog.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Size(min = 3, max = 128, message = "닉네임은 3자 이상 128자 이하로 입력해야 합니다.")
    String nickname,

    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "유효하지 않은 이메일 형식입니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, max = 256, message = "비밀번호는 8자 이상 256자 이하로 입력해야 합니다.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()\\-+])[A-Za-z\\d!@#$%^&*()\\-+]{8,256}$",
        message = "비밀번호는 최소 하나의 대문자, 소문자, 숫자 및 특수 문자(!@#$%^&*()-+)를 포함해야 합니다."
    )
    String password,

    @NotBlank(message = "이메일 인증 토큰은 필수 입력값입니다.")
    String emailVerificationToken
) {
}
