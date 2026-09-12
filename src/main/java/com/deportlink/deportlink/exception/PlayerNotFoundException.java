package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class PlayerNotFoundException extends BusinessException {
    public PlayerNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public PlayerNotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.NOT_FOUND);
    }
}
