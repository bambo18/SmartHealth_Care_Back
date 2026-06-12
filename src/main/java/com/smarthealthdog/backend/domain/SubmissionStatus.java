package com.smarthealthdog.backend.domain;

// Define this Enum in your domain package (e.g., SubmissionStatus.java)
public enum SubmissionStatus {
    DELETED,  // Submission has been deleted (not active)
    PENDING,    // Just submitted, waiting for processing
    PROCESSING, // ML model is currently running
    COMPLETED,  // Successfully processed (your current logic)
    FAILED      // Processing failed (e.g., photo corrupted, model error)
}