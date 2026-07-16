package com.smarthealthdog.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 로그인 후 발급된 인가 코드를
 * 백엔드로 전달할 때 사용하는 요청 DTO입니다.
 */
public record KakaoAuthorizationCodeRequest(

    @NotBlank(message = "카카오 인가 코드는 필수입니다.")
    String code

) {
}