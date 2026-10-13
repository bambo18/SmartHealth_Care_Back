package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.OralHealthRecord;

public interface OralHealthRecordRepository extends JpaRepository<OralHealthRecord, Long> {
    List<OralHealthRecord> findByPetIdOrderByRecordedAtDesc(Long petId);
}