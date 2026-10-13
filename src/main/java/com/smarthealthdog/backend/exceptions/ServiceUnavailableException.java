package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

/**
 * 외부 서비스(OCR 등)에 일시적으로 연결할 수 없을 때 사용한다. 503 으로 매핑된다.
 */
public class ServiceUnavailableException extends CustomException {

    public ServiceUnavailableException(ErrorCode errorCode) {
        super(errorCode);
    }
}
