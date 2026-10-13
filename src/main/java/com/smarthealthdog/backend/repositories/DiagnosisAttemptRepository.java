package com.smarthealthdog.backend.repositories;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;

@Repository
public interface DiagnosisAttemptRepository extends JpaRepository<DiagnosisAttempt, Long> {

    boolean existsByUserIdAndTypeAndAttemptedAtAfter(Long userId, SubmissionTypeEnum type, Instant attemptedAfter);

    /**
     * 제한 간격을 지난 시도 기록은 더 이상 쓸모가 없으므로 지운다.
     * 시도를 기록할 때마다 호출해 테이블이 무한히 커지지 않게 한다.
     */
    @Modifying
    @Query("DELETE FROM DiagnosisAttempt a WHERE a.userId = :userId AND a.type = :type AND a.attemptedAt < :threshold")
    int deleteStaleAttempts(
        @Param("userId") Long userId,
        @Param("type") SubmissionTypeEnum type,
        @Param("threshold") Instant threshold
    );
}
