package com.smarthealthdog.backend.validation;

import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {
    public boolean isValid(String password) {
        if (password == null) {
            return false; // Minimum length requirement
        }

        String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()\\-+])[A-Za-z\\d!@#$%^&*()\\-+]{8,256}$";
        return password.matches(regex);
    }
}
