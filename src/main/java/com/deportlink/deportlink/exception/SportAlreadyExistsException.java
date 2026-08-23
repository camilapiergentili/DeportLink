package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class SportAlreadyExistsException extends BusinessException {
    public SportAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
