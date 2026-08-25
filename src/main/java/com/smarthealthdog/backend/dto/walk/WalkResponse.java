package com.smarthealthdog.backend.dto.walk;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.domain.Walk;

public class WalkResponse {

    private static final ObjectMapper om = new ObjectMapper();

    /*
     * 기본 산책 응답
     */
    public static Map<String, Object> toSnake(Walk w) {

        Long duration = w.getDurationSeconds();

        if (duration == null
                && w.getStartTime() != null
                && w.getEndTime() != null) {

            duration = Duration
                    .between(w.getStartTime(), w.getEndTime())
                    .getSeconds();
        }

        /*
         * Map.of()는 null 값을 허용하지 않는다.
         *
         * 진행 중인 산책:
         * end_time = null
         * duration = null
         *
         * 이 가능하므로 LinkedHashMap 사용
         */
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("walk_id", w.getId());
        result.put("pet_id", w.getPet().getId());
        result.put("start_time", w.getStartTime());
        result.put("end_time", w.getEndTime());
        result.put("duration", duration);
        result.put("distance", w.getDistanceKm());
        result.put("path_coordinates", w.getPathCoordinates());

        return result;
    }

    /*
     * 산책 상세 조회용
     *
     * path_coordinates 문자열을 실제 배열 형태로 변환
     */
    public static Map<String, Object> toDetailSnake(Walk w) {

        Long duration = w.getDurationSeconds();

        if (duration == null
                && w.getStartTime() != null
                && w.getEndTime() != null) {

            duration = Duration
                    .between(w.getStartTime(), w.getEndTime())
                    .getSeconds();
        }

        List<List<Double>> coords = List.of();

        try {
            if (w.getPathCoordinates() != null
                    && !w.getPathCoordinates().isBlank()) {

                coords = om.readValue(
                        w.getPathCoordinates(),
                        new TypeReference<List<List<Double>>>() {
                        }
                );
            }
        } catch (Exception ignore) {
            // 파싱 실패 시 빈 배열 반환
        }

        /*
         * 진행 중 산책에서는
         * end_time / duration이 null일 수 있으므로
         * Map.of() 대신 LinkedHashMap 사용
         */
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("walk_id", w.getId());
        result.put("pet_id", w.getPet().getId());
        result.put("start_time", w.getStartTime());
        result.put("end_time", w.getEndTime());
        result.put("duration", duration);
        result.put("distance", w.getDistanceKm());
        result.put("path_coordinates", coords);

        // 아직 사진 기능 미도입
        result.put("photos", List.of());

        return result;
    }
}