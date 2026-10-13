package com.smarthealthdog.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 건강검진표(진단서) OCR 등록 결과.
 *
 * 원본 이미지 키는 {@link Submission#getPhotoUrl()} 에 있으므로 이 엔티티에는 두지 않는다.
 * OCR 원응답에는 견주 성명·주소가 포함되므로 화이트리스트한 12개 항목만 보관한다.
 */
@Entity
@Table(name = "pet_health_certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetHealthCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    /** 동물명 */
    @Column(name = "animal_name", length = 255)
    private String animalName;

    /** 종류 (원문. 예: Canine) */
    @Column(name = "species_text", length = 255)
    private String speciesText;

    /** 품종 */
    @Column(name = "breed", length = 255)
    private String breed;

    /** 성별 (원문. 예: Castrated Male) */
    @Column(name = "gender_text", length = 255)
    private String genderText;

    /** 모색 */
    @Column(name = "coat_color", length = 255)
    private String coatColor;

    /** 연령 (원문. 예: 16년 3개월) */
    @Column(name = "age_text", length = 255)
    private String ageText;

    /** 특징 */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /** 병명 (필수) */
    @Column(name = "disease_name", nullable = false, columnDefinition = "TEXT")
    private String diseaseName;

    /** 발병 연원일 (파싱 성공 시) */
    @Column(name = "onset_date")
    private LocalDate onsetDate;

    /** 발병 연원일 (원문) */
    @Column(name = "onset_date_text", length = 64)
    private String onsetDateText;

    /** 진단 연원일 (파싱 성공 시) */
    @Column(name = "diagnosed_date")
    private LocalDate diagnosedDate;

    /** 진단 연원일 (원문, 필수) */
    @Column(name = "diagnosed_date_text", nullable = false, length = 64)
    private String diagnosedDateText;

    /** 예후 소견 */
    @Column(name = "prognosis", columnDefinition = "TEXT")
    private String prognosis;

    /** 그외의 사항 (비고) */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "ocr_confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    /** 사용자가 한 번이라도 보정하면 true 가 되며 되돌릴 수 없다. */
    @Column(name = "is_manually_edited", nullable = false)
    @Builder.Default
    private boolean manuallyEdited = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
