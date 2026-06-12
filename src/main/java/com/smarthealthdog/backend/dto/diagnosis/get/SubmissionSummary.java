package com.smarthealthdog.backend.dto.diagnosis.get;

import java.time.Instant;

import lombok.Value;

@Value
public class SubmissionSummary {
    public String submissionId;
    public SubmissionSummaryPetInfo pet;
    public String status;
    public String type;
    public Instant submittedAt;
    public Instant completedAt;
}
