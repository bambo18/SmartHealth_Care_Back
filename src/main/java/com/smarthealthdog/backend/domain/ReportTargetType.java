package com.smarthealthdog.backend.domain;

import lombok.Getter;

@Getter
public enum ReportTargetType {
    REVIEW("review"),
    COMMENT("comment"),
    USER("user"),
    PET("pet"),
    SHELTER("shelter"),
    WALK("walk"),
    EYE_DIAGNOSIS("eye_diagnosis"),
    MOUTH_DIAGNOSIS("mouth_diagnosis");

    private final String value;

    ReportTargetType(String value) {
        this.value = value;
    }
}