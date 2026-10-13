package com.smarthealthdog.backend.validation;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
public class UpdateHealthCertificateRequestErrorCode {
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

    /**
     * 12개 항목은 모두 자유 텍스트이고 길이 제한만 걸려 있으므로 전부 INVALID_INPUT 으로 모은다.
     * 필수 항목(병명·진단 연원일)을 빈 값으로 지우려는 시도도 같은 코드로 서비스에서 거부한다.
     */
    private ErrorCode getErrorCode(String fieldName) {
        return switch (fieldName) {
            default -> INVALID_INPUT;
        };
    }
}
