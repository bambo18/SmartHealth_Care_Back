package com.smarthealthdog.backend.clients.naver;

import java.net.URI;

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

        String responseBody = restClient.get()
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
                .body(String.class);

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException(
                    "NAVER Local Search API returned an empty response."
            );
        }

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