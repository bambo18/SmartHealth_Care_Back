package com.smarthealthdog.backend.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuthClient {
    private final WebClient webClient;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String GOOGLE_USER_URL;

    @Value("${spring.security.oauth2.client.provider.kakao.user-info-uri}")
    private String KAKAO_USER_URL;

    /**
     * 카카오 사용자 정보 조회
     * @param accessToken 카카오 액세스 토큰
     * @return 카카오 사용자 정보 JSON 객체
     */
    // TODO: WebFlux를 활용한 비동기 처리로 변경 고려
    public JsonNode getKakaoUserInfo(String accessToken) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(KAKAO_USER_URL)
                        .queryParam("property_keys", "[\"kakao_account.email\",\"kakao_account.profile\"]")
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}