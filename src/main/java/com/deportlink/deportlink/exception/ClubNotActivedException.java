package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ClubNotActivedException extends BusinessException {
    public ClubNotActivedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
