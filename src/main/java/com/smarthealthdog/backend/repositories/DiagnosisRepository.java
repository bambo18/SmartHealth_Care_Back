package com.smarthealthdog.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.Diagnosis;
import com.smarthealthdog.backend.domain.Submission;

@Repository
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
    List<Diagnosis> findBySubmission(Submission submission);

    @Query("SELECT d FROM Diagnosis d " +
       "JOIN FETCH d.condition " +
       "WHERE d.submission.id = :submissionId")
    List<Diagnosis> findBySubmissionIdWithCondition(@Param("submissionId") UUID submissionId);
}
