package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ClubAlreadyExistsException extends BusinessException {
    public ClubAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
