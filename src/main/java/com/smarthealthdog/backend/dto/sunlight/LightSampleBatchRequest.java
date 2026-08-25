package com.smarthealthdog.backend.dto.sunlight;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record LightSampleBatchRequest(

        /*
         * 스마트폰에서 모아둔 조도 측정값
         *
         * 예:
         * 30초마다 측정
         * 5분에 한 번 서버 전송
         *
         * → 약 10개씩 전달
         */
        @NotNull
        List<@Valid LightSampleRequest> samples

) {
}