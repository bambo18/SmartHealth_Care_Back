package com.smarthealthdog.backend.dto.health;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 수정 화면에서 OCR 오인식을 보정하기 위한 부분 수정 요청.
 *
 * 전달된 필드만 반영한다 — null 은 "변경 없음"이고, 빈 문자열은 "지우기 시도"다.
 * 필수 항목(병명·진단 연원일)을 빈 값으로 지우려는 시도는 서비스에서 거부한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHealthCertificateRequest {

    @JsonProperty("animal_name")
    @Size(max = 255, message = "동물명은 255자 이하여야 합니다.")
    private String animalName;

    @JsonProperty("species_text")
    @Size(max = 255, message = "종류는 255자 이하여야 합니다.")
    private String speciesText;

    @JsonProperty("breed")
    @Size(max = 255, message = "품종은 255자 이하여야 합니다.")
    private String breed;

    @JsonProperty("gender_text")
    @Size(max = 255, message = "성별은 255자 이하여야 합니다.")
    private String genderText;

    @JsonProperty("coat_color")
    @Size(max = 255, message = "모색은 255자 이하여야 합니다.")
    private String coatColor;

    @JsonProperty("age_text")
    @Size(max = 255, message = "연령은 255자 이하여야 합니다.")
    private String ageText;

    @JsonProperty("features")
    private String features;

    @JsonProperty("disease_name")
    private String diseaseName;

    @JsonProperty("onset_date_text")
    @Size(max = 64, message = "발병 연원일은 64자 이하여야 합니다.")
    private String onsetDateText;

    @JsonProperty("diagnosed_date_text")
    @Size(max = 64, message = "진단 연원일은 64자 이하여야 합니다.")
    private String diagnosedDateText;

    @JsonProperty("prognosis")
    private String prognosis;

    @JsonProperty("remarks")
    private String remarks;
}
