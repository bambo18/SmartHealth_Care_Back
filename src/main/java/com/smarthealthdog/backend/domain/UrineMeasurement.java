package com.smarthealthdog.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "urine_measurements")
public class UrineMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "submission_id") // Assuming Submission entity exists
    private Submission submission;

    @ManyToOne(optional = false)
    @JoinColumn(name = "analyte_id")
    private UrineAnalyte analyte;

    @Column(length = 512, nullable = false) // Accommodate values like "Trace 15", "Neg."
    private String value; 

    // Optional RGB color for image analysis
    @Column(name = "color_r")
    @Builder.Default
    private Short colorR = 0;

    @Column(name = "color_g")
    @Builder.Default
    private Short colorG = 0;

    @Column(name = "color_b")
    @Builder.Default
    private Short colorB = 0;
}
