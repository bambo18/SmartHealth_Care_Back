package com.smarthealthdog.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable; // Needed if CheckMethod and Language aren't defined yet

// Note: This relies on the CheckMethodTranslationId class defined previously.

@Entity
@Table(name = "check_method_translations")
@IdClass(CheckMethodTranslationId.class)
@Getter
public class CheckMethodTranslation implements Serializable {

    // --- Composite Key Fields (still needed for @IdClass) ---
    @Id
    @Column(name = "check_method_id", nullable = false, insertable = false, updatable = false)
    private Integer checkMethodId;

    @Id
    @Column(name = "language_id", nullable = false, insertable = false, updatable = false)
    private Integer languageId;

    // --- Relationship Mappings ---
    
    // 1. Map to the 'CheckMethod' entity
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("checkMethodId") // Maps this relationship to the 'checkMethodId' part of the composite key
    @JoinColumn(name = "check_method_id", referencedColumnName = "id")
    private ConditionCheckMethod checkMethod;

    // 2. Map to the 'Language' entity
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("languageId") // Maps this relationship to the 'languageId' part of the composite key
    @JoinColumn(name = "language_id", referencedColumnName = "id")
    private Language language;
    
    // --- Data Fields ---
    @Column(name = "translated_name", nullable = false, length = 255)
    private String translatedName;

    @Lob 
    @Column(name = "translated_description", nullable = false, columnDefinition = "TEXT")
    private String translatedDescription;
    
    // Default Constructor, Getters, and Setters (omitted for brevity)
}