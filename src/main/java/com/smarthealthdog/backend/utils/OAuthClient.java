package com.smarthealthdog.backend.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.KakaoTokenResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthClient {

    private final ObjectMapper objectMapper;

    /**
     * Java 기본 HttpClient를 사용합니다.
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

    @Value("${kakao.oauth.rest-api-key}")
    private String KAKAO_REST_API_KEY;

    @Value("${kakao.oauth.client-secret}")
    private String KAKAO_CLIENT_SECRET;

    @Value("${kakao.oauth.redirect-uri}")
    private String KAKAO_REDIRECT_URI;

    @Value("${kakao.oauth.token-uri}")
    private String KAKAO_TOKEN_URL;

    /**
     * 카카오 인가 코드를 카카오 액세스 토큰으로 교환합니다.
     *
     * @param authorizationCode 카카오 인가 코드
     * @return 카카오 액세스 토큰
     */
    public String getKakaoAccessToken(
        String authorizationCode
    ) {
        if (
            authorizationCode == null ||
            authorizationCode.isBlank()
        ) {
            throw new IllegalArgumentException(
                "카카오 인가 코드가 비어 있습니다."
            );
        }

        String normalizedAuthorizationCode =
            authorizationCode.trim();

        String requestBody =
            "grant_type=" +
            encodeFormValue("authorization_code") +
            "&client_id=" +
            encodeFormValue(KAKAO_REST_API_KEY) +
            "&redirect_uri=" +
            encodeFormValue(KAKAO_REDIRECT_URI) +
            "&code=" +
            encodeFormValue(normalizedAuthorizationCode) +
            "&client_secret=" +
            encodeFormValue(KAKAO_CLIENT_SECRET);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(KAKAO_TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE +
                    ";charset=utf-8"
            )
            .header(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    requestBody,
                    StandardCharsets.UTF_8
                )
            )
            .build();

        /*
         * 인가 코드, REST API 키, 클라이언트 시크릿은
         * 로그에 출력하지 않습니다.
         */
        log.info(
            "카카오 액세스 토큰 발급 요청: uri={}, codeLength={}",
            KAKAO_TOKEN_URL,
            normalizedAuthorizationCode.length()
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
                    "카카오 액세스 토큰 발급 실패: status={}, response={}",
                    statusCode,
                    responseBody
                );

                throw new IllegalStateException(
                    "카카오 액세스 토큰 발급에 실패했습니다. status=" +
                    statusCode
                );
            }

            if (
                responseBody == null ||
                responseBody.isBlank()
            ) {
                throw new IllegalStateException(
                    "카카오 토큰 발급 응답이 비어 있습니다."
                );
            }

            KakaoTokenResponse tokenResponse =
                objectMapper.readValue(
                    responseBody,
                    KakaoTokenResponse.class
                );

            if (
                tokenResponse == null ||
                tokenResponse.accessToken() == null ||
                tokenResponse.accessToken().isBlank()
            ) {
                log.error(
                    "카카오 토큰 발급 응답에 access_token이 없습니다."
                );

                throw new IllegalStateException(
                    "카카오 액세스 토큰이 응답에 없습니다."
                );
            }

            log.info(
                "카카오 액세스 토큰 발급 성공: expiresIn={}",
                tokenResponse.expiresIn()
            );

            return tokenResponse.accessToken();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.error(
                "카카오 액세스 토큰 발급 요청이 중단되었습니다.",
                e
            );

            throw new IllegalStateException(
                "카카오 액세스 토큰 발급 요청이 중단되었습니다.",
                e
            );

        } catch (IOException e) {
            log.error(
                "카카오 액세스 토큰 응답 처리 중 오류가 발생했습니다: {}",
                e.getMessage(),
                e
            );

            throw new IllegalStateException(
                "카카오 액세스 토큰 응답 처리에 실패했습니다.",
                e
            );

        } catch (RuntimeException e) {
            log.error(
                "카카오 액세스 토큰 발급 중 오류가 발생했습니다: {}",
                e.getMessage(),
                e
            );

            throw e;
        }
    }

    /**
     * 카카오 액세스 토큰을 사용하여
     * 카카오 사용자 정보를 조회합니다.
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 카카오 사용자 정보 JSON
     */
    public JsonNode getKakaoUserInfo(
        String accessToken
    ) {
        if (
            accessToken == null ||
            accessToken.isBlank()
        ) {
            throw new IllegalArgumentException(
                "카카오 액세스 토큰이 비어 있습니다."
            );
        }

        String normalizedAccessToken =
            accessToken.trim();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(KAKAO_USER_URL))
            .timeout(Duration.ofSeconds(15))
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + normalizedAccessToken
            )
            .header(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .GET()
            .build();

        /*
         * 액세스 토큰 자체는 출력하지 않고
         * 길이와 헤더 존재 여부만 출력합니다.
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

            if (
                responseBody == null ||
                responseBody.isBlank()
            ) {
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

    /**
     * application/x-www-form-urlencoded 요청을 위해
     * 값을 URL 인코딩합니다.
     */
    private String encodeFormValue(String value) {
        if (value == null) {
            return "";
        }

        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        );
    }
}