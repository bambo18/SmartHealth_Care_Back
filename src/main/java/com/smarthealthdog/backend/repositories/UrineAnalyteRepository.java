package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.UrineAnalyte;

public interface UrineAnalyteRepository extends JpaRepository<UrineAnalyte, Integer> {
    public List<UrineAnalyte> findAll();
}
