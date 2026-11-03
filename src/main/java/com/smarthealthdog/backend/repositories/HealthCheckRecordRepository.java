
package com.smarthealthdog.backend.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.HealthCheckRecord;

public interface HealthCheckRecordRepository
        extends JpaRepository<HealthCheckRecord, Long> {

    Page<HealthCheckRecord> findByPetId(
            Long petId,
            Pageable pageable
    );

    Optional<HealthCheckRecord> findByIdAndPetId(
            Long id,
            Long petId
    );
}
