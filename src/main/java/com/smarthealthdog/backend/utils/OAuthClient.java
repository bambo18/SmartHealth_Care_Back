package com.smarthealthdog.backend.utils;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OAuthClient {

    /**
     * 프로젝트의 공용 WebClient Bean을 사용하지 않고,
     * 카카오 API 호출 전용 WebClient를 별도로 생성합니다.
     *
     * 공용 WebClient에 등록된 필터나 기본 설정에 의해
     * Authorization 헤더가 제거되는 문제를 방지하기 위함입니다.
     */
    private final WebClient kakaoWebClient =
        WebClient.builder().build();

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String GOOGLE_USER_URL;

    @Value("${spring.security.oauth2.client.provider.kakao.user-info-uri}")
    private String KAKAO_USER_URL;

    /**
     * 카카오 액세스 토큰으로 카카오 사용자 정보를 조회합니다.
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

        /*
         * 복사 과정에서 토큰 앞뒤에 공백 또는 줄바꿈이 들어가는 것을
         * 방지하기 위해 trim() 처리합니다.
         */
        String normalizedAccessToken = accessToken.trim();

        try {
            /*
             * 토큰 자체는 절대 로그에 출력하지 않습니다.
             * 전달 여부를 확인할 수 있도록 길이만 기록합니다.
             */
            log.info(
                "카카오 사용자 정보 조회 요청: uri={}, tokenLength={}",
                KAKAO_USER_URL,
                normalizedAccessToken.length()
            );

            JsonNode response = kakaoWebClient
                .get()
                .uri(URI.create(KAKAO_USER_URL))
                /*
                 * setBearerAuth 대신 Authorization 헤더를
                 * 명시적으로 구성합니다.
                 */
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + normalizedAccessToken
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response == null || response.isEmpty()) {
                throw new IllegalStateException(
                    "카카오 사용자 정보 응답이 비어 있습니다."
                );
            }

            log.info(
                "카카오 사용자 정보 조회 성공: kakaoUserId={}",
                response.path("id").asText("")
            );

            return response;

        } catch (WebClientResponseException e) {
            /*
             * 액세스 토큰은 로그에 남기지 않습니다.
             * 카카오가 반환한 상태와 응답 본문만 출력합니다.
             */
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