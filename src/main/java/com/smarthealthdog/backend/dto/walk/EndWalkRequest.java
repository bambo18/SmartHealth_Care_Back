package com.smarthealthdog.backend.dto.walk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
        List<List<Double>> pathCoordinates,

        /*
         * 일시정지를 제외한 실제 활동 시간 (초)
         *
         * 선택 필드
         *
         * 전달하지 않으면 기존 방식대로
         * end_time - start_time 으로 산책 시간을 계산한다.
         *
         * 상한(전체 경과 시간 초과 금지) 검증은
         * start_time을 알아야 하므로 WalkService에서 수행한다.
         */
        @Min(value = 0)
        @JsonProperty("active_duration_seconds")
        Long activeDurationSeconds

) {
}