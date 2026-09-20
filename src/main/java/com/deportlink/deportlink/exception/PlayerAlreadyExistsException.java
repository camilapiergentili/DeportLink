package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class PlayerAlreadyExistsException extends BusinessException {
    public PlayerAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
