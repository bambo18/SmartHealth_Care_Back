package com.smarthealthdog.backend.clients.naver;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.hospital.naver.NaverLocalSearchResponse;

@Component
public class NaverLocalSearchClient {

    private static final String BASE_URL =
            "https://naverapihub.apigw.ntruss.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;

    public NaverLocalSearchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${naver.search.client-id}") String clientId,
            @Value("${naver.search.client-secret}") String clientSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();

        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public NaverLocalSearchResponse search(
            String query,
            int display,
            int start
    ) {

        URI uri = UriComponentsBuilder
                .fromPath("/search/v1/local")
                .queryParam("query", query)
                .queryParam("display", display)
                .queryParam("start", start)
                .queryParam("sort", "random")
                .build()
                .encode()
                .toUri();

        /*
         * NAVER Local Search API가
         * Content-Type: text/plain;charset=UTF-8
         * 형태로 응답하는 경우가 있으므로,
         *
         * String.class로 바로 변환하지 않고
         * 원본 byte[]를 받은 뒤 UTF-8로 명시적으로 디코딩한다.
         */
        byte[] responseBytes = restClient.get()
                .uri(uri)
                .header(
                        "X-NCP-APIGW-API-KEY-ID",
                        clientId
                )
                .header(
                        "X-NCP-APIGW-API-KEY",
                        clientSecret
                )
                .retrieve()
                .body(byte[].class);

        if (responseBytes == null || responseBytes.length == 0) {
            throw new IllegalStateException(
                    "NAVER Local Search API returned an empty response."
            );
        }

        /*
         * NAVER 응답을 UTF-8로 명시적으로 변환
         */
        String responseBody = new String(
                responseBytes,
                StandardCharsets.UTF_8
        );

        /*
         * JSON 문자열을 NaverLocalSearchResponse DTO로 변환
         */
        try {
            return objectMapper.readValue(
                    responseBody,
                    NaverLocalSearchResponse.class
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to parse NAVER Local Search API response.",
                    e
            );
        }
    }
}