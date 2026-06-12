package com.smarthealthdog.backend.utils;

import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TokenGenerator {

    @Autowired
    private SecureRandom secureRandom;

    public String generateEmailVerificationCode() {

        int length = 6;
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < length; i++) {
            try {
                int index = secureRandom.nextInt(characters.length());
                code.append(characters.charAt(index));
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate secure random number", e);
            }
        }
        return code.toString();
    }

    public String generateDiagnosisUpdateToken() {
        int length = 32;
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < length; i++) {
            try {
                int index = secureRandom.nextInt(characters.length());
                token.append(characters.charAt(index));
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate secure random number", e);
            }
        }
        return token.toString();
    }
}
