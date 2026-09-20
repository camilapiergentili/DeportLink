package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class CourtAlreadyExistsException extends BusinessException {
    public CourtAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
