package com.smarthealthdog.backend.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "conditions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "species"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Condition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotBlank
    @Column(length = 255)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PetSpecies species;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToMany
    @JoinTable(
        name = "condition_check_methods",
        joinColumns = @JoinColumn(name = "condition_id"),
        inverseJoinColumns = @JoinColumn(name = "check_method_id")
    )
    private Set<ConditionCheckMethod> checkMethods;

    @OneToMany(mappedBy = "condition", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ConditionTranslation> conditionTranslations = new HashSet<>();
}