package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class BranchAlreadyExistsException extends BusinessException {
    public BranchAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
