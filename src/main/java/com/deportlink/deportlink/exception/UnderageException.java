package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class UnderageException extends BusinessException {
    public UnderageException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
