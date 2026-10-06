package com.smarthealthdog.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 건강검진표(진단서) OCR 결과 기록.
 *
 * 종류 성별 연령을 enum 이 아니라 원문 텍스트로 보관한다.
 * 진단서는 'Canine' / 'Castrated Male' / '16년 3개월' 처럼 적으며,
 * PetSpecies PetGender 로 변환하면 중성화 여부와 개월 단위 나이가 손실된다.
 *
 * 날짜는 파싱값(LocalDate)과 원문(text)을 함께 보관한다.
 * OCR 이 구분자나 숫자를 틀릴 수 있어 파싱 실패가 등록을 막지 않는다.
 *
 * 원본 이미지 키는 이 엔티티에 없다 — submission.photoUrl 을 쓴다.
 * OCR 원응답(raw json)도 저장하지 않는다 — 견주 성명 주소가 포함된다.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "pet_health_certificates")
public class PetHealthCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    // ── 진단서 12개 항목 ──

    @Column(name = "animal_name", length = 255)
    private String animalName;

    @Column(name = "species_text", length = 255)
    private String speciesText;

    @Column(name = "breed", length = 255)
    private String breed;

    @Column(name = "gender_text", length = 255)
    private String genderText;

    @Column(name = "coat_color", length = 255)
    private String coatColor;

    @Column(name = "age_text", length = 255)
    private String ageText;

    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /** 병명 — 필수. 비면 등록 자체가 성립하지 않는다. */
    @Column(name = "disease_name", nullable = false, columnDefinition = "TEXT")
    private String diseaseName;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "onset_date_text", length = 64)
    private String onsetDateText;

    @Column(name = "diagnosed_date")
    private LocalDate diagnosedDate;

    /** 진단 연원일 원문 — 필수. 파싱 실패해도 원문은 반드시 있다. */
    @Column(name = "diagnosed_date_text", nullable = false, length = 64)
    private String diagnosedDateText;

    @Column(name = "prognosis", columnDefinition = "TEXT")
    private String prognosis;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    // ── 메타 ──

    @Column(name = "ocr_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    /** 한 번이라도 사용자가 보정하면 true 가 되고 되돌릴 수 없다. */
    @Column(name = "is_manually_edited", nullable = false)
    @Builder.Default
    private boolean manuallyEdited = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
