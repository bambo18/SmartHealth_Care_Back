package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class DiagnosisAttemptLimiterUT {

    @Mock
    private DiagnosisAttemptRepository diagnosisAttemptRepository;

    @InjectMocks
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(diagnosisAttemptLimiter, "userIntervalSeconds", 30);
    }

    @Test
    void checkAndRecordAttempt_ShouldRecordAttempt_WhenNoRecentAttemptExists() {
        when(diagnosisAttemptRepository.existsByUserIdAndTypeAndAttemptedAtAfter(
            eq(USER_ID), eq(SubmissionTypeEnum.HEALTH_CERTIFICATE), any(Instant.class)
        )).thenReturn(false);

        diagnosisAttemptLimiter.checkAndRecordAttempt(USER_ID, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        ArgumentCaptor<DiagnosisAttempt> captor = ArgumentCaptor.forClass(DiagnosisAttempt.class);
        verify(diagnosisAttemptRepository).save(captor.capture());

        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, captor.getValue().getType());
    }

    @Test
    void checkAndRecordAttempt_ShouldReject_WhenRecentAttemptExists() {
        when(diagnosisAttemptRepository.existsByUserIdAndTypeAndAttemptedAtAfter(
            eq(USER_ID), eq(SubmissionTypeEnum.HEALTH_CERTIFICATE), any(Instant.class)
        )).thenReturn(true);

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(USER_ID, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(diagnosisAttemptRepository, never()).save(any());
    }

    @Test
    void checkAndRecordAttempt_ShouldCleanUpStaleRows_WhenRecordingAttempt() {
        when(diagnosisAttemptRepository.existsByUserIdAndTypeAndAttemptedAtAfter(
            eq(USER_ID), eq(SubmissionTypeEnum.HEALTH_CERTIFICATE), any(Instant.class)
        )).thenReturn(false);

        diagnosisAttemptLimiter.checkAndRecordAttempt(USER_ID, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        // 제한 간격을 지난 기록은 쓸모가 없다. 정리하지 않으면 테이블이 무한히 커진다.
        verify(diagnosisAttemptRepository).deleteStaleAttempts(
            eq(USER_ID), eq(SubmissionTypeEnum.HEALTH_CERTIFICATE), any(Instant.class)
        );
    }

    @Test
    void checkAndRecordAttempt_ShouldIsolateByType_SoTypesDoNotInterfere() {
        when(diagnosisAttemptRepository.existsByUserIdAndTypeAndAttemptedAtAfter(
            eq(USER_ID), eq(SubmissionTypeEnum.EYE), any(Instant.class)
        )).thenReturn(false);

        diagnosisAttemptLimiter.checkAndRecordAttempt(USER_ID, SubmissionTypeEnum.EYE);

        // 진단서 시도 기록이 눈 진단을 막아서는 안 된다.
        verify(diagnosisAttemptRepository).existsByUserIdAndTypeAndAttemptedAtAfter(
            eq(USER_ID), eq(SubmissionTypeEnum.EYE), any(Instant.class)
        );
        verify(diagnosisAttemptRepository, never()).existsByUserIdAndTypeAndAttemptedAtAfter(
            eq(USER_ID), eq(SubmissionTypeEnum.HEALTH_CERTIFICATE), any(Instant.class)
        );
    }

    @Test
    void checkAndRecordAttempt_ShouldRejectNullArguments() {
        assertThrows(
            IllegalArgumentException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(null, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(USER_ID, null)
        );
    }
}
