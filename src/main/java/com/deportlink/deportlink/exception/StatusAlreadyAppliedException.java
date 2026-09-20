package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class StatusAlreadyAppliedException extends BusinessException {
    public StatusAlreadyAppliedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
