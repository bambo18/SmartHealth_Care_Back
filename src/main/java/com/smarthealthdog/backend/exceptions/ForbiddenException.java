package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

public class ForbiddenException extends CustomException {
    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }
}
