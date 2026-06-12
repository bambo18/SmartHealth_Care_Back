package com.smarthealthdog.backend.dto.walk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateWalkRequest(
        @NotNull Instant startTime, // "2025-09-22T09:30:00Z"
        @NotNull Instant endTime,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal distanceKm,
        List<List<Double>> pathCoordinates  // [ [lat, lng], ... ]
) {}

//아래 어노테이션은 필요시 추가!!
//@PastOrPresent startTime이 과거이거나 현재 시각이어야 한다
//@FutureOrPresent endTime이 현재 또는 미래여야 한다
//@AssertTrue 등 startTime < endTime 같은 논리 검증하는 데 씀


