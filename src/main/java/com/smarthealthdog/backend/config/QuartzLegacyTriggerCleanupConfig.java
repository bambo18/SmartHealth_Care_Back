package com.smarthealthdog.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class QuartzLegacyTriggerCleanupConfig {

    private static final Logger log =
            LoggerFactory.getLogger(QuartzLegacyTriggerCleanupConfig.class);

    private static final String GROUP = "DEFAULT";

    private static final String LEGACY_PREFIX =
            "org.springframework.scheduling.quartz.CronTriggerFactoryBean@";

    private static final Map<String, String> EXPECTED = Map.of(
            "PushAIInferenceTasksJob", "PushAIInferenceTasksTrigger",
            "RemoveServiceErrorTasksJob", "RemoveServiceErrorTasksTrigger",
            "RetryAIInferenceTasksJob", "RetryAIInferenceTasksTrigger"
    );

    @Bean
    public ApplicationRunner quartzCleanupRunner(
            Scheduler scheduler,
            @Value("${QUARTZ_LEGACY_CLEANUP_ENABLED:false}") boolean cleanupEnabled) {
        return args -> {
            List<TriggerKey> legacyKeys = new ArrayList<>();

            // 먼저 전체 검증: 잘못된 설정이면 아무것도 삭제하지 않음
            for (Map.Entry<String, String> entry : EXPECTED.entrySet()) {
                JobKey jobKey = JobKey.jobKey(entry.getKey(), GROUP);

                boolean fixedFound = false;

                for (Trigger trigger : scheduler.getTriggersOfJob(jobKey)) {
                    TriggerKey key = trigger.getKey();

                    if (!GROUP.equals(key.getGroup())) {
                        throw new IllegalStateException(
                                "Unexpected trigger group: " + key);
                    }

                    if (entry.getValue().equals(key.getName())) {
                        fixedFound = true;
                    } else if (key.getName().startsWith(LEGACY_PREFIX)) {
                        legacyKeys.add(key);
                    } else {
                        throw new IllegalStateException(
                                "Unknown trigger found: " + key);
                    }
                }

                if (!fixedFound) {
                    throw new IllegalStateException(
                            "Fixed trigger missing: " + entry.getValue());
                }
            }

            log.info("Quartz legacy triggers to remove: {}", legacyKeys.size());

            if (!legacyKeys.isEmpty() && !cleanupEnabled) {
                throw new IllegalStateException(
                        "Legacy Quartz triggers found. Cleanup is disabled.");
            }

            // 검증된 과거 이름의 트리거만 제거
            for (TriggerKey key : legacyKeys) {
                boolean removed = scheduler.unscheduleJob(key);

                if (!removed) {
                    throw new IllegalStateException(
                            "Failed to remove trigger: " + key);
                }
            }

            // 작업당 정확히 하나의 트리거가 남았는지 확인
            for (Map.Entry<String, String> entry : EXPECTED.entrySet()) {
                JobKey jobKey = JobKey.jobKey(entry.getKey(), GROUP);

                List<? extends Trigger> remaining =
                        scheduler.getTriggersOfJob(jobKey);

                if (remaining.size() != 1 ||
                    !entry.getValue().equals(
                            remaining.get(0).getKey().getName())) {
                    throw new IllegalStateException(
                            "Quartz trigger verification failed: " + jobKey);
                }
            }

            // 검증 후 정상 스케줄링 시작
            scheduler.start();

            log.info(
                    "Quartz cleanup complete. Removed {} legacy triggers. " +
                    "Scheduler started with {} fixed triggers.",
                    legacyKeys.size(),
                    EXPECTED.size()
            );
        };
    }
}