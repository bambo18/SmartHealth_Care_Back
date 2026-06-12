package com.smarthealthdog.backend.dto.diagnosis.create;

import com.smarthealthdog.backend.domain.Submission;

public record RequestDiagnosisData (
    String imageUrl,
    Submission submission
) {}