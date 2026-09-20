package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ClubNotFoundException extends BusinessException {
    public ClubNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
