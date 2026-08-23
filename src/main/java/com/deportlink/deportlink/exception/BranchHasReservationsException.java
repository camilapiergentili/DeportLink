package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class BranchHasReservationsException extends BusinessException {
    public BranchHasReservationsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
