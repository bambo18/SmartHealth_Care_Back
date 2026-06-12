package com.smarthealthdog.backend.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.smarthealthdog.backend.domain.EmailVerification;
import com.smarthealthdog.backend.dto.auth.EmailVerificationCodeSentEvent;
import com.smarthealthdog.backend.exceptions.ForbiddenException;
import com.smarthealthdog.backend.repositories.EmailVerificationRepository;

@SpringBootTest
@ActiveProfiles("test")
public class EmailVerificationServiceTest {
    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendEmailVerification(any(EmailVerificationCodeSentEvent.class));
        // Initialize any necessary data or configurations here
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationTriesCount", 5);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationTriesDurationDays", 1);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationFailureAttempts", 5);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationLockDurationMinutes", 30);
        ReflectionTestUtils.setField(emailVerificationService, "emailVerificationSecret", "test-email-verification-secret");
        ReflectionTestUtils.setField(emailVerificationService, "allowedEmails", "test@example.com");
        emailVerificationRepository.deleteAll();
    }

    @AfterEach
    void cleanAfter() {
        emailVerificationRepository.deleteAll();
    }

    @Test
    void validateExistingEmailVerification_ShouldDoNothing_WhenNoExistingRecord() {
        emailVerificationService.sendEmailVerification("test@example.com");
    }

    @Test
    void validateExistingEmailVerification_ShouldThrowException_WhenExistingRecordExceedsLimitOnTries() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(5) // Assuming the limit is 5
            .build();

        emailVerificationRepository.save(existingRecord);
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.sendEmailVerification("test@example.com");
        });

        EmailVerification changedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(changedRecord.getEmailVerificationLockedAt() != null);
    }

    @Test
    void validateExistingEmailVerification_ShouldThrowException_WhenExistingRecordExceedsLimitOnFailures() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationFailCount(5) // Assuming the limit is 5
            .build();

        emailVerificationRepository.save(existingRecord);
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.sendEmailVerification("test@example.com");
        });

        EmailVerification changedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(changedRecord.getEmailVerificationLockedAt() != null);
    }

    @Test
    void validateExistingEmailVerification_ShouldThrowException_WhenTriesAndFailuresExceededAndLockedPeriodNotPassed() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(5)
            .emailVerificationFailCount(5) // Assuming the limit is 5
            .emailVerificationLockedAt(Instant.now().minusSeconds((3600 * 24) - 60)) // Locked 24 hours ago minus 1 minute
            .build();

        emailVerificationRepository.save(existingRecord);
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.sendEmailVerification("test@example.com");
        });

        EmailVerification unchangedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(unchangedRecord.getEmailVerificationTries() == 5);
        assertTrue(unchangedRecord.getEmailVerificationFailCount() == 5);
        assertTrue(unchangedRecord.getEmailVerificationLockedAt() != null);
    }

    @Test
    void validateExistingEmailVerification_ShouldResetTriesAndFailures_WhenLockedPeriodHasPassed() {
        EmailVerification existingRecord = EmailVerification.builder()
            .email("test@example.com")
            .emailVerificationToken("hashed-token")
            .emailVerificationTries(5)
            .emailVerificationFailCount(5) // Assuming the limit is 5
            .emailVerificationLockedAt(Instant.now().minusSeconds((3600 * 24) + 1)) // Locked 24 hours and 1 second ago
            .build();

        emailVerificationRepository.save(existingRecord);
        emailVerificationService.sendEmailVerification("test@example.com");

        EmailVerification updatedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(updatedRecord.getEmailVerificationTries() == 1, "Tries should be reset to 1, but was " + updatedRecord.getEmailVerificationTries());
        assertTrue(updatedRecord.getEmailVerificationFailCount() == 0, "Failures should be reset to 0, but was " + updatedRecord.getEmailVerificationFailCount());
        assertTrue(updatedRecord.getEmailVerificationLockedAt() == null, "LockedAt should be null, but was " + updatedRecord.getEmailVerificationLockedAt());
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

        emailVerificationRepository.save(existingRecord);
        emailVerificationService.sendEmailVerification("test@example.com");

        EmailVerification updatedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(updatedRecord.getEmailVerificationTries() == 4);
        assertTrue(updatedRecord.getEmailVerificationFailCount() == 0);
        assertTrue(updatedRecord.getEmailVerificationLockedAt() == null);
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

        emailVerificationRepository.save(existingRecord);
        assertThrows(ForbiddenException.class, () -> {
            emailVerificationService.sendEmailVerification("test@example.com");
        });

        EmailVerification unchangedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(unchangedRecord.getEmailVerificationTries() == 3);
        assertTrue(unchangedRecord.getEmailVerificationFailCount() == 5);
        assertTrue(unchangedRecord.getEmailVerificationLockedAt() != null);
    }

    @Test
    void sendEmailVerification_ShouldCreateNewRecord_WhenNoExistingRecord() {
        emailVerificationService.sendEmailVerification("test@example.com");
        EmailVerification record = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(record.getEmailVerificationTries() == 1);
        assertTrue(record.getEmailVerificationFailCount() == 0);
        assertTrue(record.getEmailVerificationLockedAt() == null);
        assertTrue(record.getEmailVerificationToken() != null && !record.getEmailVerificationToken().isEmpty());
    }

    @Test
    void sendEmailVerification_ShouldUpdateExistingRecord_WhenExistingRecordPresent() {
        emailVerificationService.sendEmailVerification("test@example.com");
        EmailVerification existingRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(existingRecord.getEmailVerificationTries() == 1);
        assertTrue(existingRecord.getEmailVerificationFailCount() == 0);
        assertTrue(existingRecord.getEmailVerificationLockedAt() == null);
        assertTrue(existingRecord.getEmailVerificationToken() != null && !existingRecord.getEmailVerificationToken().isEmpty());
        assertTrue(existingRecord.getEmailVerificationRequestedAt() != null);
        assertTrue(existingRecord.getEmailVerificationExpiry() != null);

        emailVerificationService.sendEmailVerification("test@example.com");
        EmailVerification updatedRecord = emailVerificationRepository.findByEmail("test@example.com").orElseThrow();
        assertTrue(updatedRecord.getEmailVerificationTries() == 2);
        assertTrue(updatedRecord.getEmailVerificationFailCount() == 0); // Reset to 0
        assertTrue(updatedRecord.getEmailVerificationLockedAt() == null); // Unchanged
        assertTrue(updatedRecord.getEmailVerificationToken() != null && !updatedRecord.getEmailVerificationToken().isEmpty());
        assertTrue(!updatedRecord.getEmailVerificationToken().equals("old-hashed-token")); // Token should be updated
        assertTrue(updatedRecord.getEmailVerificationRequestedAt().isAfter(existingRecord.getEmailVerificationRequestedAt()));
        assertTrue(updatedRecord.getEmailVerificationExpiry().isAfter(existingRecord.getEmailVerificationExpiry()));
    }
}
