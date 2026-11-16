package com.smarthealthdog.backend.dto.health;

import lombok.Data;

/**
 * OCR을 거치지 않는 신규 수기 등록과, OCR 완료 후 필드 보완에 공통 사용.
 * PATCH에서 null(필드 생략 포함)은 변경하지 않음; 빈 문자열은 해당 필드 삭제.
 */
@Data
public class ManualHealthCertificateRequest {
    private String animalName;
    /** 병원명 (선택) */
    private String hospitalName;
    private String speciesText;
    private String breed;
    private String genderText;
    private String coatColor;
    private String ageText;
    private String features;
    private String registrationNumber;
    private String diseaseName;
    private String onsetDateText;
    private String diagnosedDateText;
    private String symptoms;
    private String treatment;
    private String hospitalizationDischargeDateText;
    private String documentDateText;
    private String prognosis;
    private String remarks;
}
