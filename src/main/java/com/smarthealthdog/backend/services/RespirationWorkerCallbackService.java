package com.smarthealthdog.backend.services;

import java.math.BigDecimal;
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
    private final RespirationRecordRepository records;

    @Transactional
    public void reportResult(UUID videoId, BigDecimal rate) {
        RespirationRecord record = requireRecord(videoId);
        if (record.getStatus() == RespirationStatus.COMPLETED) {
            // Identical callbacks are harmless; conflicting results are rejected.
            if (record.getRespiratoryRate() != null && record.getRespiratoryRate().compareTo(rate) == 0) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicting completed result");
        }
        if (record.getStatus() != RespirationStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Record is not PROCESSING");
        }
        record.markCompleted(rate);
    }

    @Transactional
    public void reportFailure(UUID videoId, String reason) {
        RespirationRecord record = requireRecord(videoId);
        if (record.getStatus() == RespirationStatus.FAILED) {
            if (reason.equals(record.getFailureReason())) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicting failed result");
        }
        if (record.getStatus() != RespirationStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Record is not PROCESSING");
        }
        record.markInferenceFailed(reason);
    }

    private RespirationRecord requireRecord(UUID videoId) {
        return records.findByIdForUpdate(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
    }
}
