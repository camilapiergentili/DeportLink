package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class InvalidTimeRangeException extends BusinessException {
    public InvalidTimeRangeException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
