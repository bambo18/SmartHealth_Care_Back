package com.smarthealthdog.backend.dto.healthcheck;

import java.time.Instant;
import java.util.List;

public record HealthCheckResponse(
        Long id,
        Long petId,
        String species,
        int overallStage,
        String scoringVersion,
        Instant createdAt,
        List<AreaResult> areas
) {
    public record AreaResult(
            String areaKey,
            String areaTitle,
            int score,
            int stage,
            boolean urgentHit,
            List<AnswerResult> answers
    ) {}

    public record AnswerResult(
            int questionIndex,
            String questionText,
            String questionWeight,
            int answerValue
    ) {}
}
