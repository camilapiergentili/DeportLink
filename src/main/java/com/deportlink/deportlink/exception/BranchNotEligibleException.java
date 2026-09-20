package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class BranchNotEligibleException extends BusinessException {
    public BranchNotEligibleException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
