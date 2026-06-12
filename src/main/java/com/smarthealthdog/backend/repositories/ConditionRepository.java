package com.smarthealthdog.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.PetSpecies;

public interface ConditionRepository extends JpaRepository<Condition, Integer> {
    Optional<Condition> findByName(String name);
    List<Condition> findBySpecies(PetSpecies species);
}
