package com.smarthealthdog.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.DiagnosisAttempt;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.DiagnosisAttemptRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 진단 요청 빈도를 "시도" 기준으로 제한한다.
 *
 * Submission 행 기준이 아닌 이유:
 * 판별 실패 요청은 Submission 행을 만들지 않으므로,
 * 행을 세면 비용을 발생시키는 요청만 제한을 빠져나간다.
 */
@Service
@RequiredArgsConstructor
public class DiagnosisAttemptLimiter {

    private final DiagnosisAttemptRepository diagnosisAttemptRepository;

    /** (반려동물, 유형) 단위 최소 간격. 기존 눈 소변 제한과 같은 설정 키를 쓴다. */
    @Value("${inference-service.interval.seconds}")
    private int petIntervalSeconds;

    /** 사용자 단위 최소 간격. 유료 외부 API를 호출하는 유형에만 적용한다. */
    @Value("${ocr.user-interval.seconds}")
    private int userIntervalSeconds;

    /**
     * 외부 유료 API를 요청당 1회 호출하는 유형.
     * 눈 소변은 큐에 적재만 하므로 여기에 넣지 않는다.
     */
    private static final List<SubmissionTypeEnum> PAID_SYNC_TYPES = List.of(
        SubmissionTypeEnum.HEALTH_CERTIFICATE,
        SubmissionTypeEnum.ORAL
    );

    /**
     * 빈도 제한을 검사하고, 통과하면 즉시 시도를 기록한다.
     *
     * 반드시 외부 API 호출보다 앞에서 불러야 한다.
     * 검증 호출 뒤에 두면 제한이 비용을 막지 못한다.
     *
     * REQUIRES_NEW 인 이유: 이후 OCR 이 실패해 요청이 400 으로 끝나도 시도 기록은 남아야 한다.
     * 호출자 트랜잭션에 참여하면 롤백에 함께 휩쓸려 "실패 요청은 제한을 빠져나간다" 는
     * 함정이 되살아난다.
     *
     * @param pet 대상 반려동물 (owner 가 로드되어 있어야 한다)
     * @param type 제출 유형
     * @throws InvalidRequestDataException 간격 내 재요청인 경우 {@code REQUEST_TOO_FREQUENT}
     * @throws IllegalArgumentException pet 또는 type 이 null 인 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecordAttempt(Pet pet, SubmissionTypeEnum type) {
        if (pet == null || type == null) {
            throw new IllegalArgumentException("Pet 과 type 은 null 일 수 없습니다.");
        }

        Long petId = pet.getId();
        Long userId = pet.getOwner().getId();
        Instant now = Instant.now();

        // 1. (반려동물, 유형) 단위 간격
        Optional<DiagnosisAttempt> existing =
            diagnosisAttemptRepository.findByPetIdAndType(petId, type);

        if (existing.isPresent() && isWithin(existing.get().getLastAttemptedAt(), now, petIntervalSeconds)) {
            throw new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT);
        }

        // 2. 사용자 단위 간격 — 유료 유형만. 반려동물을 바꿔 우회하는 것을 막는다.
        if (PAID_SYNC_TYPES.contains(type)) {
            Optional<Instant> latestForUser =
                diagnosisAttemptRepository.findLatestAttemptForUser(userId, PAID_SYNC_TYPES);

            if (latestForUser.isPresent() && isWithin(latestForUser.get(), now, userIntervalSeconds)) {
                throw new InvalidRequestDataException(ErrorCode.REQUEST_TOO_FREQUENT);
            }
        }

        // 3. 시도 기록 — 판별 결과를 보기 전에 기록한다.
        //    새 행을 만들지 않고 기존 행을 갱신해 테이블이 자라지 않게 한다.
        DiagnosisAttempt attempt = existing.orElseGet(() ->
            DiagnosisAttempt.builder()
                .petId(petId)
                .userId(userId)
                .type(type)
                .build()
        );
        attempt.setLastAttemptedAt(now);

        diagnosisAttemptRepository.save(attempt);
    }

    private boolean isWithin(Instant last, Instant now, int intervalSeconds) {
        if (last == null) {
            return false;
        }

        return Duration.between(last, now).getSeconds() < intervalSeconds;
    }
}
