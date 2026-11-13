package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smarthealthdog.backend.domain.RespirationRecord;
import com.smarthealthdog.backend.domain.RespirationStatus;
import com.smarthealthdog.backend.repositories.RespirationRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RespirationWorkerCallbackService {
    private static final Set<String> SUPPORTED_FAILURE_REASONS = Set.of(
            "INVALID_INPUT", "TIMEOUT", "MEASUREMENT_FAILED", "INFERENCE_ERROR");

    private final RespirationRecordRepository records;

    /** New two-step API: save rate but don't advance status. */
    @Transactional
    public void saveResultOnly(UUID videoId, BigDecimal rate) {
        RespirationRecord record = requireRecord(videoId);
        if (record.getStatus() == RespirationStatus.COMPLETED) {
            if (sameRate(record.getRespiratoryRate(), rate)) {
                return; // Idempotent retry after a successful status PATCH.
            }
            throw conflict("Conflicting completed result");
        }
        if (record.getStatus() != RespirationStatus.PROCESSING) {
            throw conflict("Record is not PROCESSING");
        }
        if (record.getRespiratoryRate() != null && !sameRate(record.getRespiratoryRate(), rate)) {
            throw conflict("Conflicting respiratory rate already saved");
        }
        record.saveMeasuredRate(rate);
    }

    /** New two-step API: finish only after result is saved, or mark failure. */
    @Transactional
    public void reportStatus(UUID videoId, RespirationStatus requestedStatus, String reason) {
        if (requestedStatus == null) {
            throw badRequest("status is required");
        }
        if (requestedStatus == RespirationStatus.COMPLETED) {
            if (reason != null) {
                throw badRequest("failureReason must be omitted for COMPLETED");
            }
            RespirationRecord record = requireRecord(videoId);
            if (record.getStatus() == RespirationStatus.COMPLETED) {
                return; // Identical retry is safe.
            }
            if (record.getStatus() != RespirationStatus.PROCESSING) {
                throw conflict("Record is not PROCESSING");
            }
            if (record.getRespiratoryRate() == null) {
                throw conflict("Save respiratoryRate before COMPLETED");
            }
            record.markCompletedFromSavedRate();
            return;
        }
        if (requestedStatus == RespirationStatus.FAILED) {
            if (reason == null || !SUPPORTED_FAILURE_REASONS.contains(reason)) {
                throw badRequest("Unsupported or missing failureReason");
            }
            reportFailure(videoId, reason);
            return;
        }
        throw badRequest("Only COMPLETED or FAILED is supported");
    }

    /** Legacy one-step result callback, retained for older clients. */
    @Transactional
    public void reportResult(UUID videoId, BigDecimal rate) {
        RespirationRecord record = requireRecord(videoId);
        if (record.getStatus() == RespirationStatus.COMPLETED) {
            if (sameRate(record.getRespiratoryRate(), rate)) {
                return;
            }
            throw conflict("Conflicting completed result");
        }
        if (record.getStatus() != RespirationStatus.PROCESSING) {
            throw conflict("Record is not PROCESSING");
        }
        if (record.getRespiratoryRate() != null && !sameRate(record.getRespiratoryRate(), rate)) {
            throw conflict("Conflicting respiratory rate already saved");
        }
        record.markCompleted(rate);
    }

    /** Shared legacy/new FAILED callback. */
    @Transactional
    public void reportFailure(UUID videoId, String reason) {
        RespirationRecord record = requireRecord(videoId);
        if (record.getStatus() == RespirationStatus.FAILED) {
            if (reason.equals(record.getFailureReason())) {
                return;
            }
            throw conflict("Conflicting failed result");
        }
        if (record.getStatus() != RespirationStatus.PROCESSING) {
            throw conflict("Record is not PROCESSING");
        }
        record.markInferenceFailed(reason);
    }

    private RespirationRecord requireRecord(UUID videoId) {
        return records.findByIdForUpdate(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
    }

    private static boolean sameRate(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
