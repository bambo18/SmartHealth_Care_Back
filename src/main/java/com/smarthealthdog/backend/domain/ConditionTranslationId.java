package com.smarthealthdog.backend.domain;

import java.io.Serializable;
import java.util.Objects;

public class ConditionTranslationId implements Serializable {

    private Integer conditionId;
    private Integer languageId;

    // Default constructor (required by JPA)
    public ConditionTranslationId() {
    }

    // Parameterized constructor
    public ConditionTranslationId(Integer conditionId, Integer languageId) {
        this.conditionId = conditionId;
        this.languageId = languageId;
    }

    // Getters and Setters (omitted for brevity)

    // hashCode and equals methods (required by JPA for @IdClass)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionTranslationId that = (ConditionTranslationId) o;
        return Objects.equals(conditionId, that.conditionId) &&
               Objects.equals(languageId, that.languageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conditionId, languageId);
    }
}