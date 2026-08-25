package com.smarthealthdog.backend.dto.sunlight;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record LightSampleRequest(

        /*
         * 스마트폰에서 생성한 측정값 UUID
         */
        @NotNull
        @JsonProperty("client_sample_id")
        UUID clientSampleId,

        /*
         * 스마트폰에서 실제 조도를 측정한 시각
         */
        @NotNull
        @JsonProperty("measured_at")
        Instant measuredAt,

        /*
         * 실제 측정 Lux
         */
        @NotNull
        @JsonProperty("lux")
        Double lux

) {
}