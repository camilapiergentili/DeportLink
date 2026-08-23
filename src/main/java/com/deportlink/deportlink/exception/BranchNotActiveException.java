package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class BranchNotActiveException extends BusinessException {
    public BranchNotActiveException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
