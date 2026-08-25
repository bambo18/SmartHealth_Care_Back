package com.smarthealthdog.backend.dto.walk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record EndWalkRequest(

        /*
         * 산책 종료 시간
         */
        @NotNull
        @JsonProperty("end_time")
        Instant endTime,

        /*
         * 최종 산책 거리 (km)
         */
        @NotNull
        @DecimalMin(value = "0.00", inclusive = true)
        @JsonProperty("distance")
        BigDecimal distanceKm,

        /*
         * 최종 산책 경로
         *
         * [
         *   [위도, 경도],
         *   [위도, 경도]
         * ]
         */
        @JsonProperty("path_coordinates")
        List<List<Double>> pathCoordinates

) {
}