package com.smarthealthdog.backend.clients.naver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
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

            @Value("${naver.maps.client-id}")
            String clientId,

            @Value("${naver.maps.client-secret}")
            String clientSecret

    ) {

        this.restClient =
                restClientBuilder
                        .baseUrl(BASE_URL)
                        .build();


        this.clientId =
                clientId;


        this.clientSecret =
                clientSecret;
    }


    // ============================================================
    // 기존 병원 기능용
    // ============================================================
    //
    // 기존 코드와 호환성을 유지한다.
    //
    // ============================================================

    public NaverReverseGeocodingResponse reverseGeocode(

            double latitude,
            double longitude

    ) {

        return restClient.get()

                .uri(uriBuilder -> uriBuilder

                        .path(
                                "/map-reversegeocode/v2/gc"
                        )

                        .queryParam(
                                "coords",
                                longitude + "," + latitude
                        )

                        .queryParam(
                                "orders",
                                "legalcode,admcode"
                        )

                        .queryParam(
                                "output",
                                "json"
                        )

                        .build()
                )

                .header(
                        "x-ncp-apigw-api-key-id",
                        clientId
                )

                .header(
                        "x-ncp-apigw-api-key",
                        clientSecret
                )

                .retrieve()

                .body(
                        NaverReverseGeocodingResponse.class
                );
    }


    // ============================================================
    // 보호소 지역 검색용
    // ============================================================
    //
    // latitude / longitude
    //
    // 예:
    // 37.6582, 126.8224
    //
    // →
    //
    // 경기도
    // 고양시 덕양구
    //
    // 같은 행정구역 정보를 추출한다.
    //
    // ============================================================

    public AdministrativeRegion reverseGeocodeRegion(

            double latitude,
            double longitude

    ) {

        JsonNode response =
                restClient.get()

                        .uri(uriBuilder -> uriBuilder

                                .path(
                                        "/map-reversegeocode/v2/gc"
                                )

                                .queryParam(
                                        "coords",
                                        longitude
                                                + ","
                                                + latitude
                                )

                                .queryParam(
                                        "orders",
                                        "legalcode,admcode"
                                )

                                .queryParam(
                                        "output",
                                        "json"
                                )

                                .build()
                        )

                        .header(
                                "x-ncp-apigw-api-key-id",
                                clientId
                        )

                        .header(
                                "x-ncp-apigw-api-key",
                                clientSecret
                        )

                        .retrieve()

                        .body(
                                JsonNode.class
                        );


        if (response == null) {

            return new AdministrativeRegion(
                    null,
                    null
            );
        }


        JsonNode results =
                response.path(
                        "results"
                );


        if (!results.isArray()
                || results.isEmpty()) {

            return new AdministrativeRegion(
                    null,
                    null
            );
        }


        /*
         * 행정구역 검색이 목적이므로
         * admcode 응답을 우선 사용한다.
         *
         * admcode가 없으면 첫 번째 결과 사용.
         */
        JsonNode selected =
                null;


        for (JsonNode result : results) {

            String name =
                    result.path("name")
                            .asText();


            if ("admcode".equalsIgnoreCase(name)) {

                selected =
                        result;

                break;
            }
        }


        if (selected == null) {

            selected =
                    results.get(0);
        }


        JsonNode region =
                selected.path(
                        "region"
                );


        String province =
                textOrNull(
                        region
                                .path("area1")
                                .path("name")
                );


        String district =
                textOrNull(
                        region
                                .path("area2")
                                .path("name")
                );


        return new AdministrativeRegion(
                province,
                district
        );
    }


    private String textOrNull(
            JsonNode node
    ) {

        if (node == null
                || node.isMissingNode()
                || node.isNull()) {

            return null;
        }


        String value =
                node.asText();


        if (value == null
                || value.isBlank()) {

            return null;
        }


        return value.trim();
    }


    // ============================================================
    // 보호소 지역 검색용 내부 응답
    // ============================================================

    public record AdministrativeRegion(

            String province,

            String district

    ) {
    }
}