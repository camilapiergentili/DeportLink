package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends BusinessException {
    public InvalidStatusTransitionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
