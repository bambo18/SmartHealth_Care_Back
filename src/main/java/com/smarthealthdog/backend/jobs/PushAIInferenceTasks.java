package com.smarthealthdog.backend.jobs;

import java.util.List;
import java.util.UUID;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.dto.diagnosis.create.RequestDiagnosisData;
import com.smarthealthdog.backend.repositories.SubmissionRepository;
import com.smarthealthdog.backend.utils.DiagnosisTaskRequestClient;
import com.smarthealthdog.backend.utils.ImgUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class PushAIInferenceTasks implements Job {
    private final ImgUtils imgUtils;
    private final SubmissionRepository submissionRepository;
    private final DiagnosisTaskRequestClient diagnosisTaskRequestClient;

    @Value("${inference-service.batch.size}")
    private int batchSize;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        List<Submission> submissions = submissionRepository.findSubmissionsWaitingToBeProcessedByAmount(
            SubmissionStatus.PENDING,
            PageRequest.of(0, batchSize)
        );
        
        if (submissions.isEmpty()) {
            return;
        }

        List<RequestDiagnosisData> requestDataList = submissions.stream()
            .map(submission -> {
                String imageURL = imgUtils.getImgUrlForAIWorker(submission.getPhotoUrl());
                return new RequestDiagnosisData(imageURL, submission);
            })
            .toList();

        List<UUID> submissionIds = submissions.stream()
            .map(Submission::getId)
            .toList();

        diagnosisTaskRequestClient.sendDiagnosisTaskInBatch(requestDataList);

        // Update statuses to PROCESSING
        submissionRepository.updateStatusByIds(SubmissionStatus.PROCESSING, submissionIds);
    }
}
