package com.smarthealthdog.backend.dto.auth;

import com.smarthealthdog.backend.domain.EmailVerification;

public record EmailVerificationCodeSentEvent(
    String email,
    String token,
    EmailVerification emailVerification
) {
}