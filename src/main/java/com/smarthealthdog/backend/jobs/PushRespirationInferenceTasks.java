package com.smarthealthdog.backend.jobs;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarthealthdog.backend.domain.RespirationRecord;
import com.smarthealthdog.backend.domain.RespirationStatus;
import com.smarthealthdog.backend.repositories.RespirationRecordRepository;
import com.smarthealthdog.backend.services.RespirationInferenceDispatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "respiration.inference", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PushRespirationInferenceTasks {
    private final RespirationRecordRepository records;
    private final RespirationInferenceDispatchService dispatcher;

    @Value("${respiration.inference.batch-size:5}")
    private int batchSize;

    // This job exists ONLY when explicitly enabled. No retries or automatic
    // recovery from PROCESSING are performed in this phase.
    @Scheduled(fixedDelayString = "${respiration.inference.poll-delay-ms:20000}",
               initialDelayString = "${respiration.inference.initial-delay-ms:60000}")
    public void pushPendingVideos() {
        int limit = Math.max(1, Math.min(batchSize, 20));
        List<UUID> ids = records.findByStatusOrderByCreatedAtAsc(
                RespirationStatus.PENDING, PageRequest.of(0, limit)).stream()
                .map(RespirationRecord::getId)
                .toList();

        for (UUID id : ids) {
            try {
                // Called through a separate Spring bean so its transaction is applied.
                dispatcher.dispatchOne(id);
            } catch (Exception ex) {
                log.error("Could not dispatch respiration record {}", id, ex);
            }
        }
    }
}
