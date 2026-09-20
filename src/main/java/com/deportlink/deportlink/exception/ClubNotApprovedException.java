package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ClubNotApprovedException extends BusinessException {
    public ClubNotApprovedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
