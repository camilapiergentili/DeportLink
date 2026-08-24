package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class InvalidCancellationWindowException extends BusinessException {
    public InvalidCancellationWindowException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
