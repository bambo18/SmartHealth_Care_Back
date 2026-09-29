package com.smarthealthdog.backend.dto.hospital.naver;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverLocalSearchResponse(
        String lastBuildDate,
        int total,
        int start,
        int display,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String link,
            String category,
            String description,
            String telephone,
            String address,
            String roadAddress,
            String mapx,
            String mapy
    ) {
    }
}