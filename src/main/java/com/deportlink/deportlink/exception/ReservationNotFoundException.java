package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
