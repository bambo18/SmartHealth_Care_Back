package com.smarthealthdog.backend.domain;

public enum SubmissionTypeEnum {
    EYE,
    URINE,
    /** 건강검진표(진단서) OCR — 동기 처리. Celery 큐를 타지 않는다. */
    HEALTH_CERTIFICATE,
    /** 구강 사진 치주염 판정 — 동기 처리. Celery 큐를 타지 않는다. */
    ORAL
}
