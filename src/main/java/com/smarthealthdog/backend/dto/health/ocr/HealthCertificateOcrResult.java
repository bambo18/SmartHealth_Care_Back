package com.smarthealthdog.backend.dto.health.ocr;

import java.time.LocalDate;

/**
 * 진단서 OCR 추출 결과.
 *
 * 견주 성명 주소 발급 병원 수의사 서명 면허번호 필드는 의도적으로 없다 —
 * 추출하지도 저장하지도 않는다 (SPEC 1.5 범위 밖).
 *
 * 날짜는 원문과 파싱값을 함께 담는다. 파싱 실패 시 LocalDate 쪽만 null 이다.
 */
public record HealthCertificateOcrResult(
    String animalName,
    String speciesText,
    String breed,
    String genderText,
    String coatColor,
    String ageText,
    String features,
    String diseaseName,
    LocalDate onsetDate,
    String onsetDateText,
    LocalDate diagnosedDate,
    String diagnosedDateText,
    String prognosis,
    String remarks,

    /** 전체 블록 신뢰도의 평균. 0.0 ~ 1.0 */
    double averageConfidence,

    /** 진단서 고유 키워드가 하나라도 검출됐는지. false 면 진단서가 아니다. */
    boolean documentKeywordFound
) {}
