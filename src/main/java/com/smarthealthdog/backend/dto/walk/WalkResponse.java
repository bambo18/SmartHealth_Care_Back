package com.smarthealthdog.backend.dto.walk;

import java.time.Duration;
import java.util.Map;

import com.smarthealthdog.backend.domain.Walk;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WalkResponse {
    private static final ObjectMapper om = new ObjectMapper();

    public static Map<String, Object> toSnake(Walk w) {
        Long duration = w.getDurationSeconds();
        if (duration == null && w.getStartTime() != null && w.getEndTime() != null) {
            duration = Duration.between(w.getStartTime(), w.getEndTime()).getSeconds();
        }
        return Map.of(
            "walk_id", w.getId(),
            "pet_id", w.getPet().getId(),
            "start_time", w.getStartTime(),
            "end_time", w.getEndTime(),
            "duration", duration,
            "distance", w.getDistanceKm(),
            "path_coordinates", w.getPathCoordinates()
        );
    }

    /** 상세 조회용: 경로를 배열로 파싱하고 photos 필드도 포함 */
    public static Map<String, Object> toDetailSnake(Walk w) {
        Long duration = w.getDurationSeconds();
        if (duration == null && w.getStartTime() != null && w.getEndTime() != null) {
            duration = Duration.between(w.getStartTime(), w.getEndTime()).getSeconds();
        }

        List<List<Double>> coords = List.of();
        try {
            if (w.getPathCoordinates() != null && !w.getPathCoordinates().isBlank()) {
                coords = om.readValue(w.getPathCoordinates(),
                        new TypeReference<List<List<Double>>>() {});
            }
        } catch (Exception ignore) { /* 파싱 실패 시 빈 배열 */ }

        return Map.of(
            "walk_id", w.getId(),
            "pet_id", w.getPet().getId(),
            "start_time", w.getStartTime(),
            "end_time", w.getEndTime(),
            "duration", duration,
            "distance", w.getDistanceKm(),
            "path_coordinates", coords,
            "photos", List.of() // 아직 사진 기능 미도입 → 빈 배열
        );
    }
}
