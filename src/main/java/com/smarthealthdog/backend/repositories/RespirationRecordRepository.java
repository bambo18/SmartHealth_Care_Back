package com.smarthealthdog.backend.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.RespirationRecord;
import com.smarthealthdog.backend.domain.RespirationStatus;

import jakarta.persistence.LockModeType;

public interface RespirationRecordRepository extends JpaRepository<RespirationRecord, UUID> {
    List<RespirationRecord> findTop50ByPet_IdOrderByCreatedAtDesc(Long petId);

    List<RespirationRecord> findByStatusOrderByCreatedAtAsc(RespirationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RespirationRecord r WHERE r.id = :id")
    Optional<RespirationRecord> findByIdForUpdate(@Param("id") UUID id);
}
