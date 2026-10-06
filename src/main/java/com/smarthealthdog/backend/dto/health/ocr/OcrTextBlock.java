package com.smarthealthdog.backend.dto.health.ocr;

/**
 * OCR 이 읽어낸 텍스트 조각 하나와 그 신뢰도.
 * 벤더 응답 구조를 내부로 들이지 않기 위한 중간 표현이다.
 */
public record OcrTextBlock(String text, double confidence) {}
