
package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.HealthCheckAnswer;

public interface HealthCheckAnswerRepository
        extends JpaRepository<HealthCheckAnswer, Long> {

    List<HealthCheckAnswer> findByAreaIdOrderByQuestionIndexAsc(
            Long areaId
    );
}
