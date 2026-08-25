package com.smarthealthdog.backend.dto.walk;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record CreateWalkRequest(

        /*
         * 산책 시작 시간
         *
         * 예:
         * 2026-08-25T04:00:00Z
         *
         * 한국시간 13:00 = UTC 04:00
         */
        @NotNull
        Instant startTime

) {
}