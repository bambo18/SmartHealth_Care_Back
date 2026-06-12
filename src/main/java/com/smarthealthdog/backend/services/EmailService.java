package com.smarthealthdog.backend.services;

import com.smarthealthdog.backend.dto.auth.EmailVerificationCodeSentEvent;

public interface EmailService {
    /**
     * 이메일 인증 메일 발송 (비동기)
     * @param EmailVerificationCodeSentEvent event
     */
    void sendEmailVerification(EmailVerificationCodeSentEvent event);
}