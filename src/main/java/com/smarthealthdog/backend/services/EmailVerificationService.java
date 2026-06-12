package com.smarthealthdog.backend.services;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.dto.auth.EmailVerificationCodeSentEvent;
import com.smarthealthdog.backend.exceptions.ForbiddenException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.utils.TokenGenerator;
import com.smarthealthdog.backend.validation.ErrorCode;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class EmailVerificationService {
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationRepository emailVerificationRepository;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder tokenEncoder;
    private final EntityManager entityManager;

    @Value("${app.token.email-verification.expiration.minutes}")
    private int emailVerificationExpiryMinutes;

    @Value("${app.token.email-verification.tries.count}")
    private int emailVerificationTriesCount;

    @Value("${app.token.email-verification.tries.duration.days}")
    private int emailVerificationTriesDurationDays;

    @Value("${app.token.email-verification.failure.attempts}")
    private int emailVerificationFailureAttempts;

    @Value("${app.token.email-verification.lock.duration.minutes}")
    private int emailVerificationLockDurationMinutes;

    @Value("${app.token.email-verification.secret}")
    private String emailVerificationSecret;

    @Value("${app.mail.allowed-emails}")
    private String allowedEmails;

    /**
     * 이메일 인증 토큰 생성 및 이메일 전송
     * @param email
     */
    @Transactional
    public void sendEmailVerification(String email) {
        validateExistingEmailVerification(email);

        String token = tokenGenerator.generateEmailVerificationCode();
        String hashedToken = tokenEncoder.encode(token + emailVerificationSecret);

        // allowedEmails 설정이 있는 경우, 해당 이메일로만 인증 메일 발송
        // 개발 및 테스트 환경에서만 사용
        if (allowedEmails != null && !allowedEmails.isEmpty()) {
            String[] allowedEmailArray = allowedEmails.split(",");
            boolean isAllowed = false;
            for (String allowedEmail : allowedEmailArray) {
                if (email.equalsIgnoreCase(allowedEmail.trim())) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                return;
            }
        }

        Instant now = Instant.now();
        EmailVerification existingVerification = emailVerificationRepository.findByEmail(email).orElse(null);
        if (existingVerification == null) {
            EmailVerification emailVerification = EmailVerification.builder()
                .email(email)
                .emailVerificationToken(hashedToken)
                .emailVerificationTries(1)
                .emailVerificationRequestedAt(now)
                .emailVerificationExpiry(now.plusSeconds(emailVerificationExpiryMinutes * 60))
                .emailVerificationFailCount(0)
                .build();

            emailVerificationRepository.save(emailVerification);

            EmailVerificationCodeSentEvent event = new EmailVerificationCodeSentEvent(email, token, emailVerification);
            eventPublisher.publishEvent(event);
            return;
        }

        existingVerification.setEmailVerificationToken(hashedToken);
        existingVerification.setEmailVerificationRequestedAt(now);
        existingVerification.setEmailVerificationExpiry(now.plusSeconds(emailVerificationExpiryMinutes * 60));
        existingVerification.setEmailVerificationFailCount(0);
        existingVerification.setEmailVerificationLockedAt(null);
        emailVerificationRepository.save(existingVerification);

        // 시도 횟수 1 증가
        emailVerificationRepository.incrementEmailVerificationTriesByEmail(email);

        EmailVerificationCodeSentEvent event = new EmailVerificationCodeSentEvent(email, token, existingVerification);
        eventPublisher.publishEvent(event);
    }

    /**
     * 기존 이메일 인증 레코드가 있는지 확인하고, 있으면 잠금 상태인지 확인.
     * 기존 레코드가 잠금 상태이거나, 시도 횟수 또는 실패 횟수가 초과된 경우 예외 발생.
     * 특정 기간이 지난 경우 시도 횟수 및 실패 횟수 초기화.
     * @param email
     * @throws ForbiddenException 잠금 상태일 경우 발생
     */
    public void validateExistingEmailVerification(String email) {
        EmailVerification existingVerification = emailVerificationRepository.findByEmail(email).orElse(null);
        if (existingVerification == null) {
            return;
        }

        int emailVerificationTries = existingVerification.getEmailVerificationTries();
        int failCount = existingVerification.getEmailVerificationFailCount();
        Instant lockedAt = existingVerification.getEmailVerificationLockedAt(); 

        // 시도 횟수 초과 확인
        // Edge Case: emailVerificationTries가 emailVerificationTriesCount를 초과하는 경우
        if (emailVerificationTries >= emailVerificationTriesCount) {
            if (lockedAt != null && Instant.now().isBefore(lockedAt.plusSeconds(emailVerificationTriesDurationDays * 24 * 60 * 60))) {
                throw new ForbiddenException(ErrorCode.EMAIL_VERIFICATION_TRIES_EXCEEDED);
            }

            // 잠금이 없을 경우, 잠금 상태로 설정
            if (lockedAt == null) {
                emailVerificationRepository.lockEmailVerificationByEmail(email, Instant.now());
                throw new ForbiddenException(ErrorCode.EMAIL_VERIFICATION_TRIES_EXCEEDED);
            }

            // 시도 횟수 초기화
            existingVerification.setEmailVerificationTries(0);
            existingVerification.setEmailVerificationFailCount(0);
            existingVerification.setEmailVerificationLockedAt(null);
            emailVerificationRepository.save(existingVerification);
            return;
        }

        // 실패 횟수가 초과했는지 확인
        if (failCount >= emailVerificationFailureAttempts) {
            // 인증 보낸 시간이 잠금 기간 내에 있는지 확인
            if (lockedAt != null && Instant.now().isBefore(lockedAt.plusSeconds(emailVerificationLockDurationMinutes * 60))) {
                throw new ForbiddenException(ErrorCode.EMAIL_VERIFICATION_FAIL_COUNT_EXCEEDED);
            }

            // 잠금이 없을 경우, 잠금 상태로 설정
            if (lockedAt == null) {
                emailVerificationRepository.lockEmailVerificationByEmail(email, Instant.now());
                throw new ForbiddenException(ErrorCode.EMAIL_VERIFICATION_FAIL_COUNT_EXCEEDED);
            }

            // 실패 횟수 초기화
            existingVerification.setEmailVerificationFailCount(0);
            existingVerification.setEmailVerificationLockedAt(null);
            emailVerificationRepository.save(existingVerification);
            return;
        }
    }

    /**
     * 이메일 인증 토큰 검증
     * @param email
     * @param token
     * @throws InvalidRequestDataException 토큰이 유효하지 않을 경우 발생
     */
    public void verifyEmailToken(String email, String token) {
        if (email == null || token == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        }

        EmailVerification emailVerification = emailVerificationRepository.findByEmail(email).orElse(null);
        if (emailVerification == null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_JWT);
        }

        // 잠금 상태인지 확인
        Instant lockedAt = emailVerification.getEmailVerificationLockedAt();
        if (lockedAt != null) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        }

        // 토큰 인증 시간이 만료되었는지 확인
        Instant expiry = emailVerification.getEmailVerificationExpiry();
        if (expiry == null || Instant.now().isAfter(expiry)) {
            emailVerificationRepository.incrementEmailVerificationFailCountByEmail(email);
            entityManager.refresh(emailVerification); // 영속성 컨텍스트에서 최신 상태로 갱신

            // 실패 횟수가 초과했는지 확인
            if (emailVerification.getEmailVerificationFailCount() >= emailVerificationFailureAttempts) {
                emailVerificationRepository.lockEmailVerificationByEmail(email, Instant.now());
                throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL_VERIFICATION);
            }

            throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        }

        // 토큰이 일치하는지 확인
        String hashedToken = emailVerification.getEmailVerificationToken();
        if (hashedToken == null || !tokenEncoder.matches(token + emailVerificationSecret, hashedToken)) {
            emailVerificationRepository.incrementEmailVerificationFailCountByEmail(email);
            entityManager.refresh(emailVerification); // 영속성 컨텍스트에서 최신 상태로 갱신

            // 실패 횟수가 초과했는지 확인
            if (emailVerification.getEmailVerificationFailCount() >= emailVerificationFailureAttempts) {
                emailVerificationRepository.lockEmailVerificationByEmail(email, Instant.now());
                throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL_VERIFICATION);
            }

            throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        } 

        // 토큰이 유효한 경우 이메일 인증 레코드 삭제
        emailVerificationRepository.deleteByEmail(email);
    }
}
