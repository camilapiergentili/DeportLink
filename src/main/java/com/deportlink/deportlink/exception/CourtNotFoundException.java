package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class CourtNotFoundException extends BusinessException {
    public CourtNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
