package com.smarthealthdog.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Apply the Validation Constraints here:
    @NotBlank(message = "Email is required.") // Ensures it's not null or whitespace
    @Email(message = "Please provide a valid email address.") // Ensures valid format
    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(
        name = "email_verification_tries",
        columnDefinition = "INT DEFAULT 0",
        nullable = false
    )
    private int emailVerificationTries;

    @Column(name = "email_verification_requested_at")
    private Instant emailVerificationRequestedAt;

    @Column(name = "email_verification_expiry")
    private Instant emailVerificationExpiry;

    @Column(
        name = "email_verification_fail_count",
        columnDefinition = "INT DEFAULT 0",
        nullable = false
    )
    private int emailVerificationFailCount;

    @Column(name = "email_verification_locked_at")
    private Instant emailVerificationLockedAt;
}
