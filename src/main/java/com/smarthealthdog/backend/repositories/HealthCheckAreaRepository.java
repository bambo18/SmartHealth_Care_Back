
package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.HealthCheckArea;

public interface HealthCheckAreaRepository
        extends JpaRepository<HealthCheckArea, Long> {

    List<HealthCheckArea> findByRecordId(
            Long recordId
    );
}
