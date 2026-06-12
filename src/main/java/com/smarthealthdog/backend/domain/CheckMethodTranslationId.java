package com.smarthealthdog.backend.domain;

import java.io.Serializable;
import java.util.Objects;

public class CheckMethodTranslationId implements Serializable {

    private Integer checkMethodId;
    private Integer languageId;

    // Default constructor (required by JPA)
    public CheckMethodTranslationId() {
    }

    // Parameterized constructor
    public CheckMethodTranslationId(Integer checkMethodId, Integer languageId) {
        this.checkMethodId = checkMethodId;
        this.languageId = languageId;
    }

    // Getters and Setters (omitted for brevity)

    // hashCode and equals methods (required by JPA for @IdClass)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckMethodTranslationId that = (CheckMethodTranslationId) o;
        return Objects.equals(checkMethodId, that.checkMethodId) &&
               Objects.equals(languageId, that.languageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkMethodId, languageId);
    }
}