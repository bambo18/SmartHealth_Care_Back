package com.smarthealthdog.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "species", nullable = false)
    private PetSpecies species;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private PetGender gender;

    @Column(name = "breed", length = 255)
    private String breed;

    @Column(name = "weight_kg", precision = 4, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "is_neutered", nullable = false)
    private Boolean isNeutered;

    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Pet(User owner, String name, PetSpecies species, String breed, PetGender gender, 
        LocalDate birthday, Boolean isNeutered, BigDecimal weightKg) {
        this.owner = owner;
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.gender = gender == null ? PetGender.UNKNOWN : gender;
        this.birthday = birthday;
        this.isNeutered = isNeutered == null ? false : isNeutered;
        this.weightKg = weightKg == null ? BigDecimal.ZERO : weightKg;
    }

    //수정메서드: 나중에 수정api(pull)호출하면 이 메서드 사용해서 필드 값 갱신, 역시 null 들어오면 기본값으로 처리
    public void update(String name, PetSpecies species, String breed, PetGender gender, 
        LocalDate birthday, Boolean isNeutered, BigDecimal weightKg, String profileImage) {
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.gender = gender != null ? gender : PetGender.UNKNOWN;
        this.birthday = birthday;
        this.isNeutered = isNeutered != null ? isNeutered : false;
        this.weightKg = weightKg != null ? weightKg : BigDecimal.ZERO;
        this.profileImage = profileImage;
    }
}