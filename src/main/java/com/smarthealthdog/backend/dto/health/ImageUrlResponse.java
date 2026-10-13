package com.smarthealthdog.backend.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 제출 원본 이미지의 단기 서명 URL 응답.
 *
 * 리다이렉트가 아니라 JSON 으로 돌려준다 — 프론트가 만료를 알고 재요청 시점을 판단해야 한다.
 */
public record ImageUrlResponse(
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("expires_in") long expiresIn
) {}
