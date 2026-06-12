package com.smarthealthdog.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "diagnosis_results")
public class DiagnosisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_species", nullable = false)
    private PetSpecies petSpecies;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_part")
    private DiagnosisBodyPart bodyPart;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}