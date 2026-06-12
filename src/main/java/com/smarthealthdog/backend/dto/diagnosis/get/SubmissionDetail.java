package com.smarthealthdog.backend.dto.diagnosis.get;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;

import lombok.Value;

@Value
public class SubmissionDetail<T> {
    private final UUID id;
    private final SubmissionSummaryPetInfo petInfo;
    private final SubmissionTypeEnum type;
    private final String photoUrl;
    private final String status; // String representation of the SubmissionStatus
    private final Instant submittedAt;
    private final Instant completedAt;
    private final SubmissionFailureReasonEnum failureReason;
    
    // The collection of all diagnoses associated with this submission
    private final Set<T> results;
}
