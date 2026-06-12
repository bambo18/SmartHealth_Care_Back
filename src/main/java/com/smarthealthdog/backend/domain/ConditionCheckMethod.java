package com.smarthealthdog.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "check_methods")
@Data
public class ConditionCheckMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 255, nullable = false)
    private String name; // e.g., "Urine Analysis"

    @Column(columnDefinition = "TEXT")
    private String description; // e.g., "Checks for urinary tract infections"
}