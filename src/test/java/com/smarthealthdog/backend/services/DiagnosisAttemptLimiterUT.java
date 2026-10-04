package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

@ExtendWith(MockitoExtension.class)
public class DiagnosisAttemptLimiterUT {

    @InjectMocks
    private DiagnosisAttemptLimiter diagnosisAttemptLimiter;

    @Mock private DiagnosisAttemptRepository diagnosisAttemptRepository;
    @Captor private ArgumentCaptor<DiagnosisAttempt> attemptCaptor;

    private Pet pet;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(diagnosisAttemptLimiter, "petIntervalSeconds", 60);
        ReflectionTestUtils.setField(diagnosisAttemptLimiter, "userIntervalSeconds", 10);

        User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L);

        pet = mock(Pet.class);
        when(pet.getId()).thenReturn(3L);
        when(pet.getOwner()).thenReturn(owner);
    }

    @Test
    void 기록이_없으면_통과하고_시도를_기록한다() {
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.empty());
        when(diagnosisAttemptRepository.findLatestAttemptForUser(anyLong(), anyList()))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
            diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        verify(diagnosisAttemptRepository).save(attemptCaptor.capture());
        assertEquals(3L, attemptCaptor.getValue().getPetId());
        assertEquals(7L, attemptCaptor.getValue().getUserId());
        assertEquals(SubmissionTypeEnum.HEALTH_CERTIFICATE, attemptCaptor.getValue().getType());
    }

    @Test
    void 같은_유형을_간격_내에_다시_요청하면_REQUEST_TOO_FREQUENT를_던지고_기록하지_않는다() {
        DiagnosisAttempt recent = DiagnosisAttempt.builder()
            .petId(3L)
            .userId(7L)
            .type(SubmissionTypeEnum.HEALTH_CERTIFICATE)
            .lastAttemptedAt(Instant.now().minusSeconds(5))
            .build();

        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.of(recent));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(diagnosisAttemptRepository, never()).save(any(DiagnosisAttempt.class));
    }

    @Test
    void 다른_유형의_최근_요청은_간섭하지_않는다() {
        // 5.5(a): 진단서를 올린 직후 눈 진단이 거부되던 교차 간섭이 사라져야 한다.
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.EYE))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
            diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.EYE)
        );
    }

    @Test
    void 유료_유형은_반려동물을_바꿔도_사용자_단위_간격에_걸린다() {
        // 검토 집중 지점 5: pet 단위 제한만 있으면 반려동물을 여러 마리 등록해 우회할 수 있다.
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.ORAL))
            .thenReturn(Optional.empty());
        when(diagnosisAttemptRepository.findLatestAttemptForUser(
                7L,
                List.of(SubmissionTypeEnum.HEALTH_CERTIFICATE, SubmissionTypeEnum.ORAL)
        )).thenReturn(Optional.of(Instant.now().minusSeconds(3)));

        InvalidRequestDataException e = assertThrows(
            InvalidRequestDataException.class,
            () -> diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.ORAL)
        );

        assertEquals(ErrorCode.REQUEST_TOO_FREQUENT, e.getErrorCode());
        verify(diagnosisAttemptRepository, never()).save(any(DiagnosisAttempt.class));
    }

    @Test
    void 무료_유형에는_사용자_단위_간격을_적용하지_않는다() {
        // 눈·소변은 큐에 적재만 하므로 유료 호출이 아니다. 사용자 단위 제한을 걸지 않는다.
        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.URINE))
            .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
            diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.URINE)
        );

        verify(diagnosisAttemptRepository, never()).findLatestAttemptForUser(anyLong(), anyList());
    }

    @Test
    void 간격이_지난_기록은_새_행을_만들지_않고_갱신한다() {
        DiagnosisAttempt old = DiagnosisAttempt.builder()
            .petId(3L)
            .userId(7L)
            .type(SubmissionTypeEnum.HEALTH_CERTIFICATE)
            .lastAttemptedAt(Instant.now().minusSeconds(600))
            .build();

        when(diagnosisAttemptRepository.findByPetIdAndType(3L, SubmissionTypeEnum.HEALTH_CERTIFICATE))
            .thenReturn(Optional.of(old));
        when(diagnosisAttemptRepository.findLatestAttemptForUser(anyLong(), anyList()))
            .thenReturn(Optional.of(Instant.now().minusSeconds(600)));

        diagnosisAttemptLimiter.checkAndRecordAttempt(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);

        verify(diagnosisAttemptRepository).save(attemptCaptor.capture());
        // 테이블이 무한히 자라지 않도록 기존 행 인스턴스를 그대로 저장해야 한다.
        assertEquals(old, attemptCaptor.getValue());
    }
}
