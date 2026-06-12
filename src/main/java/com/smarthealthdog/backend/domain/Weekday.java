package com.smarthealthdog.backend.domain;

import lombok.Getter;

@Getter
public enum Weekday {
    SUNDAY("sunday"),
    MONDAY("monday"),
    TUESDAY("tuesday"),
    WEDNESDAY("wednesday"),
    THURSDAY("thursday"),
    FRIDAY("friday"),
    SATURDAY("saturday");

    private final String value;

    Weekday(String value) {
        this.value = value;
    }
}