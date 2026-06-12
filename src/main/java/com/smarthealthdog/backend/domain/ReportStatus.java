package com.smarthealthdog.backend.domain;

import lombok.Getter;

@Getter
public enum ReportStatus {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    RESOLVED("resolved"),
    REJECTED("rejected");

    private final String value;

    ReportStatus(String value) {
        this.value = value;
    }
}