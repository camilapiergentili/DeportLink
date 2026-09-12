package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class CourtHasReservationsException extends BusinessException {
    public CourtHasReservationsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
