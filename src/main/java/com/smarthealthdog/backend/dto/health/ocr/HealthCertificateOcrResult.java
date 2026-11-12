package com.smarthealthdog.backend.dto.health.ocr;

/**
 * OCR 벤더가 돌려준 결과를 벤더 중립적으로 표현한 값 객체.
 *
 * 원응답 전체는 견주 성명·주소를 포함하므로 저장하지 않고 메모리에서만 다룬다.
 *
 * @param fullText 인식된 전체 텍스트
 * @param confidence 전체 평균 신뢰도 (0.0 ~ 1.0)
 */
public record HealthCertificateOcrResult(
    String fullText,
    double confidence
) {}
