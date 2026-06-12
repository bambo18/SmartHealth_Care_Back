package com.smarthealthdog.backend.repositories;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.smarthealthdog.backend.domain.EmailVerification;

@DataJpaTest
@ActiveProfiles("test")
public class EmailVerificationRepositoryTest {
    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Test
    void deleteByEmail_ShouldDeleteRecord_WhenEmailExists() {
        // Given
        String email = "test@example.com";
        EmailVerification token = EmailVerification.builder()
            .email(email)
            .emailVerificationToken("hashed-token")
            .emailVerificationExpiry(java.time.Instant.now().plusSeconds(3600))
            .emailVerificationFailCount(0)
            .build();

        emailVerificationRepository.save(token);

        if (emailVerificationRepository.findByEmail(email).isEmpty()) {
            throw new RuntimeException("Setup failed: EmailVerification record was not saved.");
        }

        // When
        emailVerificationRepository.deleteByEmail(email);

        // Then
        assertTrue(emailVerificationRepository.findByEmail(email).isEmpty());
    }

    @Test
    void getByEmail_ShouldReturnRecord_WhenEmailExists() {
        // Given
        String email = "test@example.com";
        EmailVerification token = EmailVerification.builder()
            .email(email)
            .emailVerificationToken("hashed-token")
            .emailVerificationExpiry(java.time.Instant.now().plusSeconds(3600))
            .emailVerificationFailCount(0)
            .build();

        emailVerificationRepository.save(token);
        if (emailVerificationRepository.findByEmail(email).isEmpty()) {
            throw new RuntimeException("Setup failed: EmailVerification record was not saved.");
        }

        // When
        EmailVerification retrievedToken = emailVerificationRepository.getByEmail(email);
        // Then
        assertTrue(retrievedToken != null && retrievedToken.getEmail().equals(email));
    }

    @Test
    void findByEmail_ShouldReturnRecord_WhenEmailExists() {
        // Given
        String email = "test@example.com";
        EmailVerification token = EmailVerification.builder()
            .email(email)
            .emailVerificationToken("hashed-token")
            .emailVerificationExpiry(java.time.Instant.now().plusSeconds(3600))
            .emailVerificationFailCount(0)
            .build();

        emailVerificationRepository.save(token);
        if (emailVerificationRepository.findByEmail(email).isEmpty()) {
            throw new RuntimeException("Setup failed: EmailVerification record was not saved.");
        }

        // When
        EmailVerification retrievedToken = emailVerificationRepository.findByEmail(email).orElse(null);
        // Then
        assertTrue(retrievedToken != null && retrievedToken.getEmail().equals(email));
    }
}