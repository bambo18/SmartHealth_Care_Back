package com.smarthealthdog.backend.domain;

import lombok.Getter;

@Getter
public enum DiagnosisBodyPart {
    EYE("eye"),
    MOUTH("mouth");

    private final String value;

    DiagnosisBodyPart(String value) {
        this.value = value;
    }
}