package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ClubHasBranchesException extends BusinessException {
    public ClubHasBranchesException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
