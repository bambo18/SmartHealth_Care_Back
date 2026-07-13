package com.smarthealthdog.backend.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthClient {

    private final ObjectMapper objectMapper;

    /**
     * WebClient 대신 Java 17 기본 HttpClient를 사용합니다.
     *
     * HTTP/1.1을 명시하여 카카오 API 요청 과정에서
     * Authorization 헤더가 누락되는 문제를 피합니다.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String GOOGLE_USER_URL;

    @Value("${spring.security.oauth2.client.provider.kakao.user-info-uri}")
    private String KAKAO_USER_URL;

    /**
     * 카카오 액세스 토큰을 사용하여 카카오 사용자 정보를 조회합니다.
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

        String normalizedAccessToken = accessToken.trim();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(KAKAO_USER_URL))
            .timeout(Duration.ofSeconds(15))
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + normalizedAccessToken
            )
            .header(
                HttpHeaders.ACCEPT,
                "application/json"
            )
            .GET()
            .build();

        /*
         * 토큰 값 자체는 출력하지 않고,
         * 헤더 존재 여부와 토큰 길이만 확인합니다.
         */
        log.info(
            "카카오 사용자 정보 조회 요청: uri={}, tokenLength={}, authorizationHeaderPresent={}",
            KAKAO_USER_URL,
            normalizedAccessToken.length(),
            request.headers()
                .firstValue(HttpHeaders.AUTHORIZATION)
                .isPresent()
        );

        try {
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                    StandardCharsets.UTF_8
                )
            );

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode < 200 || statusCode >= 300) {
                log.error(
                    "카카오 사용자 정보 조회 실패: status={}, response={}",
                    statusCode,
                    responseBody
                );

                throw new IllegalStateException(
                    "카카오 사용자 정보 조회에 실패했습니다. status=" +
                    statusCode
                );
            }

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException(
                    "카카오 사용자 정보 응답이 비어 있습니다."
                );
            }

            JsonNode kakaoUserInfo =
                objectMapper.readTree(responseBody);

            String kakaoUserId =
                kakaoUserInfo.path("id").asText("");

            if (kakaoUserId.isBlank()) {
                log.error(
                    "카카오 사용자 정보에 사용자 ID가 없습니다. response={}",
                    responseBody
                );

                throw new IllegalStateException(
                    "카카오 사용자 ID가 응답에 없습니다."
                );
            }

            log.info(
                "카카오 사용자 정보 조회 성공: kakaoUserId={}",
                kakaoUserId
            );

            return kakaoUserInfo;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.error(
                "카카오 사용자 정보 요청이 중단되었습니다.",
                e
            );

            throw new IllegalStateException(
                "카카오 사용자 정보 요청이 중단되었습니다.",
                e
            );

        } catch (IOException e) {
            log.error(
                "카카오 사용자 정보 응답 처리 중 오류가 발생했습니다: {}",
                e.getMessage(),
                e
            );

            throw new IllegalStateException(
                "카카오 사용자 정보 응답 처리에 실패했습니다.",
                e
            );

        } catch (RuntimeException e) {
            log.error(
                "카카오 사용자 정보 요청 중 오류가 발생했습니다: {}",
                e.getMessage(),
                e
            );

            throw e;
        }
    }
}