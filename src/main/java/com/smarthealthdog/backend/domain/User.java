package com.smarthealthdog.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.github.f4b6a3.uuid.UuidCreator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    @Builder.Default
    private UUID publicId = UuidCreator.getTimeOrderedEpoch();

    @Email(message = "유효한 이메일 형식이 아닙니다.")
    @Column(name = "email", nullable = false, length = 320, unique = true)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "nickname", nullable = false, length = 128)
    private String nickname;

    @Column(name = "profile_pic", columnDefinition = "TEXT")
    private String profilePic;

    @Column(
        name = "login_attempt", 
        nullable = false,
        columnDefinition = "INT DEFAULT 0"
    )
    private int loginAttempt;

    @Column(name = "login_attempt_started_at")
    private Instant loginAttemptStartedAt;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private Instant passwordResetTokenExpiry;
    
    @Column(name = "password_reset_requested_at")
    private Instant passwordResetRequestedAt;

    @Column(
        name = "password_reset_token_verify_fail_count", 
        columnDefinition = "INT DEFAULT 0",
        nullable = false
    )
    private int passwordResetTokenVerifyFailCount;

    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "email_verification_expiry")
    private Instant emailVerificationExpiry;

    @Column(name = "email_verification_requested_at")
    private Instant emailVerificationRequestedAt;

    @Column(
        name = "email_verification_tries",
        columnDefinition = "INT DEFAULT 0",
        nullable = false
    )
    private int emailVerificationTries;

    @Column(
        name = "email_verification_fail_count", 
        columnDefinition = "INT DEFAULT 0", 
        nullable = false
    )
    private int emailVerificationFailCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}