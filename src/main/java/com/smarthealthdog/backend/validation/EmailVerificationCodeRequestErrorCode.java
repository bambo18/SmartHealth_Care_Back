package com.smarthealthdog.backend.validation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
public class EmailVerificationCodeRequestErrorCode {
    private final ErrorCode INVALID_EMAIL = ErrorCode.INVALID_EMAIL;

    public List<ErrorCode> getErrorCode(MethodArgumentNotValidException e) {
        if (e == null || e.getBindingResult() == null) {
            return List.of(ErrorCode.INVALID_INPUT);
        }

        List<String> fields = e.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField())
            .toList();

        if (fields.isEmpty()) {
            return List.of(ErrorCode.INVALID_INPUT);
        }

        return fields.stream()
            .map(fieldName -> getErrorCode(fieldName))
            .distinct()
            .toList();
    }

    private ErrorCode getErrorCode(String fieldName) {
        return switch (fieldName) {
            case "email" -> INVALID_EMAIL;
            default -> ErrorCode.INVALID_INPUT;
        };
    }
}
