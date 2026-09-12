package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class SportNotFoundException extends BusinessException {
    public SportNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
