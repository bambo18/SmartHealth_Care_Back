package com.smarthealthdog.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 토큰 발급 API 응답 DTO입니다.
 *
 * 카카오에서 받은 access_token을 이용해
 * 사용자 정보 조회 API를 호출합니다.
 */
public record KakaoTokenResponse(

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("expires_in")
    Long expiresIn,

    @JsonProperty("refresh_token")
    String refreshToken,

    @JsonProperty("refresh_token_expires_in")
    Long refreshTokenExpiresIn,

    String scope,

    @JsonProperty("id_token")
    String idToken

) {
}