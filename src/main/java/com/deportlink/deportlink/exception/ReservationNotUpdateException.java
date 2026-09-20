package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ReservationNotUpdateException extends BusinessException {
    public ReservationNotUpdateException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
