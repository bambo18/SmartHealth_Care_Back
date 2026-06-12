package com.smarthealthdog.backend.dto.walk;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;

public record EndWalkRequest(
        @NotNull OffsetDateTime start_time,
        @NotNull OffsetDateTime end_time,
        @NotNull Double distance,
        List<List<Double>> path_coordinates  // [ [lat, lng], ... ]
) {}
