package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.exceptions.ForbiddenException;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;
import com.smarthealthdog.backend.utils.TokenGenerator;

@ExtendWith(MockitoExtension.class)
public class EmailVerificationServiceUT {
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private PasswordEncoder tokenEncoder;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        // Initialize any necessary mocks or test data here
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationTriesCount", 5);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationTriesDurationDays", 1);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationFailureAttempts", 5);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationLockDurationMinutes", 30);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationSecret", "test-email-verification-secret");
        ReflectionTestUtils.setField(emailVerificationService, "allowedEmails", "test@example.com");
    }

    @Test
    void validateExistingEmailVerification_ShouldDoNothing_WhenNoExistingRecord() {
        when(emailVerificationRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        emailVerificationService.sendEmailVerification("test@example.com");
    }

    @Test
    void validateExistingEmailVerification_ShouldThrowException_WhenExistingRecordExceedsLimitOnTries() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(5) // Assuming the limit is 5
            .build();

        when(emailVerificationRepository.findByEmail(anyString())).thenReturn(Optional.of(existingRecord));
        when(emailVerificationRepository.lockEmailVerificationByEmail(anyString(), any(Instant.class))).thenReturn(1);
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.validateExistingEmailVerification("test@example.com");
        });
    }

    @Test
    void validateExistingEmailVerification_ShouldThrowException_WhenExistingRecordExceedsLimitOnFailures() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(3)
            .emailVerificationFailCount(5) // Assuming the limit is 5
            .build();

        when(emailVerificationRepository.findByEmail(anyString())).thenReturn(Optional.of(existingRecord));
        when(emailVerificationRepository.lockEmailVerificationByEmail(anyString(), any(Instant.class))).thenReturn(1);
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.validateExistingEmailVerification("test@example.com");
        });
    }

    @Test
    void validateExistingEmailVerification_ShouldResetFailures_WhenLockedPeriodHasPassed() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(3)
            .emailVerificationFailCount(5) // Assuming the limit is 5
            .emailVerificationLockedAt(Instant.now().minusSeconds(3600)) // Locked an hour ago
            .build();

        when(emailVerificationRepository.findByEmail(anyString())).thenReturn(Optional.of(existingRecord));
        when(emailVerificationRepository.save(existingRecord)).thenReturn(existingRecord);

        emailVerificationService.validateExistingEmailVerification("test@example.com");
        verify(emailVerificationRepository).save(existingRecord);
    }

    @Test
    void validateExistingEmailVerification_ShouldThrowException_WhenLockedPeriodNotPassed() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(3)
            .emailVerificationFailCount(5) // Assuming the limit is 5
            .emailVerificationLockedAt(Instant.now().minusSeconds(60)) // Locked a minute ago
            .build();

        when(emailVerificationRepository.findByEmail(anyString())).thenReturn(Optional.of(existingRecord));
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.validateExistingEmailVerification("test@example.com");
        });
    }

    @Test
    void validateExistingEmailVerification_ShouldDoNothing_WhenUnderLimits() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(3)
            .emailVerificationFailCount(2) // Under the limit
            .build();

        when(emailVerificationRepository.findByEmail(anyString())).thenReturn(Optional.of(existingRecord));
        emailVerificationService.validateExistingEmailVerification("test@example.com");
        // No exception should be thrown
    }
}