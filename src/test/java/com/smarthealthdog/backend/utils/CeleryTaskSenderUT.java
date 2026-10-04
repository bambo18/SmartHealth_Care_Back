package com.smarthealthdog.backend.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.diagnosis.create.RequestDiagnosisData;
import com.smarthealthdog.backend.repositories.SubmissionRepository;

/**
 * 동기 처리 유형이 Celery 큐로 새어나가지 않는지 고정한다.
 *
 * 기존 코드는 if (EYE) {...} else {소변} 구조여서 신규 유형이 조용히
 * 소변 분기로 빠졌다. 그러면 Python 워커가 진단서 이미지를 소변 스트립으로
 * 판정해 엉뚱한 결과를 기록한다.
 */
@ExtendWith(MockitoExtension.class)
public class CeleryTaskSenderUT {

    @InjectMocks
    private CeleryTaskSender celeryTaskSender;

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SubmissionRepository submissionRepository;

    private RequestDiagnosisData dataOfType(SubmissionTypeEnum type) {
        Submission submission = mock(Submission.class);
        when(submission.getType()).thenReturn(type);

        // RequestDiagnosisData 의 순서는 (imageUrl, submission) 이다 — 뒤집지 않도록 주의한다.
        return new RequestDiagnosisData("https://example.com/x.jpg", submission);
    }

    @Test
    void sendDiagnosisTaskInBatch_진단서_유형은_큐로_보내지_않고_예외를_던진다() {
        RequestDiagnosisData data = dataOfType(SubmissionTypeEnum.HEALTH_CERTIFICATE);

        assertThrows(
            RuntimeException.class,
            () -> celeryTaskSender.sendDiagnosisTaskInBatch(List.of(data))
        );
    }

    @Test
    void sendDiagnosisTaskInBatch_구강_유형은_큐로_보내지_않고_예외를_던진다() {
        RequestDiagnosisData data = dataOfType(SubmissionTypeEnum.ORAL);

        assertThrows(
            RuntimeException.class,
            () -> celeryTaskSender.sendDiagnosisTaskInBatch(List.of(data))
        );
    }

    @Test
    void sendDiagnosisTaskInBatch_동기_유형이면_Redis에도_DB에도_쓰지_않는다() {
        // 예외만 확인하면 "큐에 넣은 뒤 터졌다"와 구별되지 않는다.
        RequestDiagnosisData data = dataOfType(SubmissionTypeEnum.HEALTH_CERTIFICATE);

        assertThrows(
            RuntimeException.class,
            () -> celeryTaskSender.sendDiagnosisTaskInBatch(List.of(data))
        );

        verify(stringRedisTemplate, never()).opsForList();
        verify(submissionRepository, never()).saveAll(any());
        verify(stringRedisTemplate, never()).convertAndSend(anyString(), any());
    }
}
