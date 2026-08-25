package com.smarthealthdog.backend.dto.sunlight;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SunlightProgressResponse(

        @JsonProperty("date")
        LocalDate date,

        /*
         * 일일 목표
         *
         * 내부적으로는 Lux × minute 값
         * 2,000 Lux × 30분 = 60,000
         */
        @JsonProperty("target_lux_minutes")
        int targetLuxMinutes,

        /*
         * 현재 인정된 누적값
         */
        @JsonProperty("achieved_lux_minutes")
        int achievedLuxMinutes,

        /*
         * 조건을 충족한 시간
         */
        @JsonProperty("qualifying_minutes")
        int qualifyingMinutes,

        /*
         * 조건을 만족한 10분 구간 수
         */
        @JsonProperty("qualified_windows")
        int qualifiedWindows,

        /*
         * 오늘 계산에 사용된 조도 샘플 수
         */
        @JsonProperty("sample_count")
        int sampleCount,

        /*
         * 0 ~ 100
         */
        @JsonProperty("progress_percent")
        double progressPercent,

        /*
         * 60,000 도달 여부
         */
        @JsonProperty("completed")
        boolean completed

) {
}