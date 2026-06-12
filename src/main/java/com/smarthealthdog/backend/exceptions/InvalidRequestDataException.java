package com.smarthealthdog.backend.exceptions;

import com.smarthealthdog.backend.validation.ErrorCode;

public class InvalidRequestDataException extends CustomException {
    public InvalidRequestDataException(ErrorCode errorCode) {
        super(errorCode);
    }
}
