package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * 외부 서비스(OCR·추론)에 일시적으로 연결할 수 없을 때 던진다.
 * GlobalExceptionHandler 가 503 으로 변환한다.
 */
public class ServiceUnavailableException extends CustomException {
    public ServiceUnavailableException(ErrorCode errorCode) {
        super(errorCode);
    }
}
