package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.UrineMeasurement;

public interface UrineMeasurementRepository extends JpaRepository<UrineMeasurement, Long> {
    @Query("SELECT um FROM UrineMeasurement um JOIN FETCH um.analyte WHERE um.submission = :submission")
    List<UrineMeasurement> findMeasurementBySubmissionWithAnalyte(Submission submission);
}
