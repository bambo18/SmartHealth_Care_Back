package com.smarthealthdog.backend.dto.diagnosis.get;

import com.smarthealthdog.backend.domain.PetSpecies;

import lombok.Value;

@Value
public class SubmissionSummaryPetInfo {
    public Long id;
    public String name;
    public PetSpecies species;
}
