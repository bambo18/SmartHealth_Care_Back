package com.smarthealthdog.backend.clients.naver;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.smarthealthdog.backend.dto.hospital.naver.NaverLocalSearchResponse;

@Component
public class NaverLocalSearchClient {

    private static final String BASE_URL =
            "https://naverapihub.apigw.ntruss.com";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public NaverLocalSearchClient(
            RestClient.Builder restClientBuilder,
            @Value("${naver.search.client-id}") String clientId,
            @Value("${naver.search.client-secret}") String clientSecret
    ) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();

        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public NaverLocalSearchResponse search(String query, int display, int start) {

        URI uri = UriComponentsBuilder
                .fromPath("/search/v1/local")
                .queryParam("query", query)
                .queryParam("display", display)
                .queryParam("start", start)
                .queryParam("sort", "random")
                .build()
                .encode()
                .toUri();

        return restClient.get()
                .uri(uri)
                .header("X-NCP-APIGW-API-KEY-ID", clientId)
                .header("X-NCP-APIGW-API-KEY", clientSecret)
                .retrieve()
                .body(NaverLocalSearchResponse.class);
    }
}