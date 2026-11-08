package com.smarthealthdog.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.RespirationRecord;

public interface RespirationRecordRepository extends JpaRepository<RespirationRecord, UUID> {
    List<RespirationRecord> findTop50ByPet_IdOrderByCreatedAtDesc(Long petId);
}
