package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class BranchNotApprovedException extends BusinessException {
    public BranchNotApprovedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
