package com.smarthealthdog.backend.validation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
public class CreateSocialKakaoUserRequestErrorCode {
    private final ErrorCode INVALID_SOCIAL_ACCESS_TOKEN = ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN;
    private final ErrorCode INVALID_INPUT = ErrorCode.INVALID_INPUT;

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
            case "accessToken" -> INVALID_SOCIAL_ACCESS_TOKEN;
            default -> INVALID_INPUT;
        };
    }
}
