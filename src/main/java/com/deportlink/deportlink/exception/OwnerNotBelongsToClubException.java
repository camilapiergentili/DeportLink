package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class OwnerNotBelongsToClubException extends BusinessException {
    public OwnerNotBelongsToClubException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
