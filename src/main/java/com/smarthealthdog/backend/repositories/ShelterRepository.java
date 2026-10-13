package com.smarthealthdog.backend.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.Shelter;

public interface ShelterRepository extends JpaRepository<Shelter, Long> {
	List<Shelter> findAllByNameIn(Collection<String> names);
}