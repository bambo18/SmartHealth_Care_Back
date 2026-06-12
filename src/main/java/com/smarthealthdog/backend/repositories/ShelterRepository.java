package com.smarthealthdog.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.Shelter;

public interface ShelterRepository extends JpaRepository<Shelter, Long> {
}