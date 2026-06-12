package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

public class InternalServerErrorException extends CustomException {

    public InternalServerErrorException(ErrorCode errorCode) {
        super(errorCode);
    }
}
