package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class BranchNotFoundException extends BusinessException {
    public BranchNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
