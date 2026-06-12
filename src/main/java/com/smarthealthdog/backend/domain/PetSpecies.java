package com.smarthealthdog.backend.domain;

import lombok.Getter;

@Getter
public enum PetSpecies {
    DOG("dog"),
    CAT("cat");

    private final String value;

    PetSpecies(String value) {
        this.value = value;
    }
}
