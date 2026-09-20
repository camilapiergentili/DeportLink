package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class OwnerAlreadyExistsException extends BusinessException {
    public OwnerAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
