package com.smarthealthdog.backend.dto.diagnosis.update;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 워커가 진단서에서 뽑아낸 17개 항목.
 *
 * 전부 선택 항목이다. 워커가 채우지 못한 값은 null 로 두면 되고, 백엔드가
 * {@code HealthCertificateFieldExtractor} 로 {@code rawText} 에서 다시 뽑는다.
 * 블록 전체가 없어도 된다 — 그 경우 17개 항목 모두 백엔드가 추출한다.
 *
 * <p><b>연월일은 원문 그대로 보낸다.</b> {@code LocalDate} 변환은 백엔드가 한다 —
 * 진단서마다 표기가 제각각이고(2020.10.28 / 2020년 10월 28일 / 20.10.28),
 * 정규화 규칙이 바뀔 때 워커를 재배포하지 않으려면 원문이 남아 있어야 한다.
 *
 * <p>견주 성명·주소는 추출 대상이 아니므로 이 DTO 에 존재하지 않는다.
 */
@Data
public class CertificateOcrFieldsDto {

    /** 동물명 */
    @Size(max = 255)
    private String animalName;

    /** 종류 (개·고양이 등 진단서 표기 그대로) */
    @Size(max = 255)
    private String speciesText;

    /** 품종 */
    @Size(max = 255)
    private String breed;

    /** 성별 (중성화 여부 포함 표기 그대로) */
    @Size(max = 255)
    private String genderText;

    /** 모색 */
    @Size(max = 255)
    private String coatColor;

    /** 연령 (표기 그대로) */
    @Size(max = 255)
    private String ageText;

    /** 특징 */
    private String features;

    /** 병명 — 백엔드 필수 항목. 비면 해당 제출은 실패 처리된다. */
    private String diseaseName;

    /** 발병 연원일 원문 */
    @Size(max = 64)
    private String onsetDateText;

    /** 진단 연월일 원문 — 백엔드 필수 항목. 비면 해당 제출은 실패 처리된다. */
    @Size(max = 64)
    private String diagnosedDateText;

    /** 예후 소견 */
    private String prognosis;

    /** 그 외의 사항 (비고) */
    private String remarks;

    /** 동물 등록번호 */
    @Size(max = 32)
    private String registrationNumber;

    /** 주요 증상 */
    private String symptoms;

    /** 치료 또는 처치 내용 */
    private String treatment;

    /** 입원/퇴원일 표기 (범위일 수 있으므로 문자열 유지) */
    @Size(max = 128)
    private String hospitalizationDischargeDateText;

    /** 문서 작성/발급일 표기 */
    @Size(max = 64)
    private String documentDateText;
}
