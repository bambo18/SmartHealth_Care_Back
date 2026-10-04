package com.smarthealthdog.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;

@Repository
public interface DiagnosisAttemptRepository extends JpaRepository<DiagnosisAttempt, Long> {

    Optional<DiagnosisAttempt> findByPetIdAndType(Long petId, SubmissionTypeEnum type);

    /**
     * 사용자가 지정한 유형들에 대해 가장 최근에 시도한 시각을 반환한다.
     * 반려동물을 여러 마리 등록해 pet 단위 제한을 우회하는 것을 막는다.
     */
    @Query("SELECT MAX(a.lastAttemptedAt) FROM DiagnosisAttempt a WHERE a.userId = :userId AND a.type IN :types")
    Optional<Instant> findLatestAttemptForUser(
        @Param("userId") Long userId,
        @Param("types") List<SubmissionTypeEnum> types
    );
}
