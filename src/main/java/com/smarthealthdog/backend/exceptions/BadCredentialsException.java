package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

public class BadCredentialsException extends CustomException {
    public BadCredentialsException(ErrorCode errorCode) {
        super(errorCode);
    }
}
