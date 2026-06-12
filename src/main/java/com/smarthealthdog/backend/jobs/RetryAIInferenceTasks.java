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
            Page<Submission> submissions = submissionRepository.findByStatusAndSubmittedAtLessThanEqualOrderBySubmittedAtAsc(
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
