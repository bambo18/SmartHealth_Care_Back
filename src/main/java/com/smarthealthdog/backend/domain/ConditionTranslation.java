package com.smarthealthdog.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "condition_translations")
@IdClass(ConditionTranslationId.class)
@Setter
@Getter
public class ConditionTranslation {

    @Id
    @Column(name = "condition_id", nullable = false)
    private Integer conditionId;

    @Id
    @Column(name = "language_id", nullable = false)
    private Integer languageId;

    @Column(name = "translated_name", nullable = false, length = 255)
    private String translatedName;

    @Column(name = "translated_description", nullable = false, columnDefinition = "TEXT")
    private String translatedDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conditionId") // Maps this relationship to the 'conditionId' part of the composite key
    @JoinColumn(name = "condition_id", referencedColumnName = "id")
    private Condition condition;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("languageId") // Maps this relationship to the 'languageId' part of the composite key
    @JoinColumn(name = "language_id", referencedColumnName = "id")
    private Language language;

    // Getters and Setters (omitted for brevity)
}