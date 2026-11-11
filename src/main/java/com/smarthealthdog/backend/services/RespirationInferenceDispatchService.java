package com.smarthealthdog.backend.services;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.RespirationRecord;
import com.smarthealthdog.backend.domain.RespirationStatus;
import com.smarthealthdog.backend.repositories.RespirationRecordRepository;
import com.smarthealthdog.backend.utils.RespirationCeleryTaskSender;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RespirationInferenceDispatchService {
    private final RespirationRecordRepository records;
    private final RespirationVideoStorageService storageService;
    private final RespirationCeleryTaskSender sender;

    /**
     * Per-record transaction with a write lock. Failed Redis pushes roll back the
     * PROCESSING transition. Redis+SQL is NOT a distributed transaction: if the
     * process crashes after Redis accepts the message but before SQL commits,
     * duplicate dispatch is still possible. Do not enable this until the worker
     * implements safe, idempotent processing and callback rules.
     */
    @Transactional
    public void dispatchOne(UUID id) {
        RespirationRecord record = records.findByIdForUpdate(id).orElse(null);
        if (record == null || record.getStatus() != RespirationStatus.PENDING) {
            return;
        }
        if (record.getActualSizeBytes() == null || record.getUploadedAt() == null) {
            throw new IllegalStateException("PENDING video has no completed upload: " + id);
        }

        UUID taskId = UUID.randomUUID();
        String url = storageService.createDownloadUrl(record.getStorageKey());
        sender.enqueue(url, record.getId(), taskId);
        record.markProcessing(taskId);
    }
}
