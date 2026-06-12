package com.smarthealthdog.backend.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 해당 문자열이 유효한 JSON 구조인지 검사합니다.
     *
     * @param jsonString 검사할 JSON 문자열
     * @return 유효한 JSON 구조이면 true, 그렇지 않으면 false
     */
    public boolean isValidJsonStructure(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        try {
            // Attempt to read the JSON string into a generic JsonNode tree.
            // If the string is malformed, readTree() will throw a JsonProcessingException.
            objectMapper.readTree(jsonString);

            return true;
        } catch (JsonProcessingException e) {
            // This exception is thrown if the JSON structure is invalid
            // (e.g., missing quotes, misplaced commas, unclosed brackets).
            return false;
        }
    }
}