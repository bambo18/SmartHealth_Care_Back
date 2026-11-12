package com.smarthealthdog.backend.dto.healthcheck;

import java.time.Instant;

public record HealthCheckSummaryResponse(
        Long id,
        Long petId,
        String species,
        int overallStage,
        String scoringVersion,
        Instant createdAt
) {}
