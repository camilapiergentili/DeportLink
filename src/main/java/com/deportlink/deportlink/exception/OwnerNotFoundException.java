package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class OwnerNotFoundException extends BusinessException {
    public OwnerNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
