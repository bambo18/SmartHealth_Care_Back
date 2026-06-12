package com.smarthealthdog.backend.dto.diagnosis.create;

import com.smarthealthdog.backend.domain.Submission;

public record SubmissionImageUploadEvent(
    Submission submission,
    byte[] fileBytes,
    String originalFilename,
    String contentType
) {}
