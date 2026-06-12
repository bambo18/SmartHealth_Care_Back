package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

public class ResourceNotFoundException extends CustomException {
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
