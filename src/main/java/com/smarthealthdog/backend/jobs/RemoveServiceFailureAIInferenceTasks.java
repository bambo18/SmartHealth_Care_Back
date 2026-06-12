package com.smarthealthdog.backend.jobs;

import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionFailureReasonEnum;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.repositories.SubmissionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class RemoveServiceFailureAIInferenceTasks implements Job {
    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        List<SubmissionStatus> statuses = List.of(SubmissionStatus.FAILED);
        List<SubmissionFailureReasonEnum> failureReasons = List.of(
            SubmissionFailureReasonEnum.SERVICE_ERROR,
            SubmissionFailureReasonEnum.TIMEOUT
        );

        Integer page = 0;
        while (true) {
            Page<Submission> errorSubmissions = submissionRepository.findByStatusInAndFailureReasonIn(
                statuses,
                failureReasons,
                PageRequest.of(page, 100)
            );

            if (errorSubmissions.isEmpty()) {
                return;
            }

            submissionRepository.updateStatusByIds(
                SubmissionStatus.DELETED,
                errorSubmissions.stream().map(Submission::getId).toList()
            );

            page++;
        }
    }
}
