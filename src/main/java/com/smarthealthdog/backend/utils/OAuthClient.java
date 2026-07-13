package com.smarthealthdog.backend.utils;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthClient {

    private final WebClient webClient;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String GOOGLE_USER_URL;

    @Value("${spring.security.oauth2.client.provider.kakao.user-info-uri}")
    private String KAKAO_USER_URL;

    /**
     * 카카오 액세스 토큰으로 사용자 정보를 조회합니다.
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 카카오 사용자 정보 JSON
     */
    public JsonNode getKakaoUserInfo(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                "카카오 액세스 토큰이 비어 있습니다."
            );
        }

        try {
            return webClient
                .get()
                // 전체 URL을 path가 아닌 URI로 전달합니다.
                .uri(URI.create(KAKAO_USER_URL))
                .headers(headers ->
                    headers.setBearerAuth(accessToken)
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        } catch (WebClientResponseException e) {
            // 액세스 토큰은 로그에 절대 출력하지 않습니다.
            log.error(
                "카카오 사용자 정보 조회 실패: status={}, response={}",
                e.getStatusCode(),
                e.getResponseBodyAsString()
            );

            throw e;

        } catch (Exception e) {
            log.error(
                "카카오 사용자 정보 요청 중 오류 발생: {}",
                e.getMessage(),
                e
            );

            throw e;
        }
    }
}