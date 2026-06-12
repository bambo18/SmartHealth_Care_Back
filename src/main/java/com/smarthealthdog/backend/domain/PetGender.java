package com.smarthealthdog.backend.domain;

import lombok.Getter;

@Getter
public enum PetGender {
    MALE("male"),
    FEMALE("female"),
    UNKNOWN("unknown");

    private final String value;

    PetGender(String value) {
        this.value = value;
    }
}
