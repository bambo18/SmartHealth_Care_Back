package com.smarthealthdog.backend.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Entity
@Table(name = "diagnoses", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"submission_id", "condition_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnosis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // Many-to-one relationship with Submission
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    // Many-to-one relationship with Condition (the lookup table)
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "condition_id", nullable = false)
    private Condition condition;

    @NotNull
    @Column(precision = 5, scale = 4, nullable = false, name = "probability")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal probability;

    @NotNull
    @Column(length = 32, nullable = false, name = "model_md5_hash")
    private String modelMd5Hash;
}
