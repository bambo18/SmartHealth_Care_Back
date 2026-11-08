package com.smarthealthdog.backend.dto.respiration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.smarthealthdog.backend.domain.RespirationRecord;

public record RespirationRecordResponse(
        UUID videoId,
        Long petId,
        String status,
        Integer durationSec,
        Long fileSizeBytes,
        BigDecimal respiratoryRate,
        String failureReason,
        Instant createdAt,
        Instant uploadedAt,
        Instant completedAt
) {
    public static RespirationRecordResponse from(RespirationRecord record) {
        return new RespirationRecordResponse(
                record.getId(),
                record.getPet().getId(),
                record.getStatus().name(),
                record.getDurationSec(),
                record.getActualSizeBytes(),
                record.getRespiratoryRate(),
                record.getFailureReason(),
                record.getCreatedAt(),
                record.getUploadedAt(),
                record.getCompletedAt()
        );
    }
}
