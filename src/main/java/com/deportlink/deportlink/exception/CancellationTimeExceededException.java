package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class CancellationTimeExceededException extends BusinessException {
    public CancellationTimeExceededException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
