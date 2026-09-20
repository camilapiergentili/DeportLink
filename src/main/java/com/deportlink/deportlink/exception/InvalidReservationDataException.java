package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class InvalidReservationDataException extends BusinessException {
    public InvalidReservationDataException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
