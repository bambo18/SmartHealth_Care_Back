package com.smarthealthdog.backend.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.utils.DiagnosisTaskRequestClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class RetryAIInferenceTasks implements Job {
    private final SubmissionRepository submissionRepository;
    private final DiagnosisTaskRequestClient diagnosisTaskRequestClient;

    @Value("${inference-service.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${inference-service.timeout.seconds}")
    private int inferenceServiceTimeoutSeconds;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        Integer page = 0;
        while (true) {
            // 기준은 제출 시각이 아니라 PROCESSING 진입 시각이다.
            // 제출 시각은 재시도해도 바뀌지 않아, 한 번 기준선을 넘으면 조건이
            // 영구히 참이 되어 시도마다 잡 주기만큼만 주어진다.
            Page<Submission> submissions = submissionRepository.findByStatusAndProcessingStartedAtLessThanEqualOrderByProcessingStartedAtAsc(
                SubmissionStatus.PROCESSING,
                Instant.now().minusSeconds(inferenceServiceTimeoutSeconds),
                PageRequest.of(page, 100)
            );

            if (submissions.isEmpty()) {
                return;
            }

            List<String> celeryTaskStrings = new ArrayList<>();
            for (Submission submission : submissions) {
                if (submission.getRetryCount() < maxRetryAttempts) {
                    submission.setRetryCount(submission.getRetryCount() + 1);
                    submission.setStatus(SubmissionStatus.PENDING);
                } else {
                    submission.setStatus(SubmissionStatus.FAILED);
                    submission.setFailureReason(SubmissionFailureReasonEnum.TIMEOUT);
                }

                if (submission.getCeleryTaskString() != null) {
                    celeryTaskStrings.add(submission.getCeleryTaskString());
                }
            }

            submissionRepository.saveAll(submissions);
            diagnosisTaskRequestClient.removeDiagnosisTasksInBatch(celeryTaskStrings);

            page++;
        }
    }
}
