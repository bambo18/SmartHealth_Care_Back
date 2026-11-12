package com.smarthealthdog.backend.dto.healthcheck;

import java.util.List;
import java.util.Map;

public record HealthCheckCreateRequest(
        String species,
        Map<String, List<Integer>> answers
) {}
