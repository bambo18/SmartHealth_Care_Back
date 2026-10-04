package com.smarthealthdog.backend.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 단기 서명 URL 응답.
 *
 * 302 리다이렉트가 아니라 JSON 으로 돌려준다.
 * 프론트가 만료 시점을 알고 재요청 타이밍을 판단해야 하기 때문이다.
 */
public record ImageUrlResponse(
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("expires_in") long expiresIn
) {}
