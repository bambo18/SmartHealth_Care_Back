package com.smarthealthdog.backend.clients.naver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.smarthealthdog.backend.dto.hospital.naver.NaverReverseGeocodingResponse;

@Component
public class NaverReverseGeocodingClient {

    private static final String BASE_URL =
            "https://maps.apigw.ntruss.com";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public NaverReverseGeocodingClient(
            RestClient.Builder restClientBuilder,
            @Value("${naver.maps.client-id}") String clientId,
            @Value("${naver.maps.client-secret}") String clientSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();

        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public NaverReverseGeocodingResponse reverseGeocode(
            double latitude,
            double longitude
    ) {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/map-reversegeocode/v2/gc")
                        .queryParam("coords", longitude + "," + latitude)
                        .queryParam("orders", "legalcode,admcode")
                        .queryParam("output", "json")
                        .build())
                .header("x-ncp-apigw-api-key-id", clientId)
                .header("x-ncp-apigw-api-key", clientSecret)
                .retrieve()
                .body(NaverReverseGeocodingResponse.class);
    }
}