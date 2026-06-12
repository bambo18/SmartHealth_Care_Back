package com.smarthealthdog.backend.dto.diagnosis.get;

import lombok.Value;

@Value
public class ConditionTranslationResult {
    private final String name;
    private final String description;
}