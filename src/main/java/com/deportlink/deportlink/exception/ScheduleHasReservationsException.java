package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ScheduleHasReservationsException extends BusinessException {
    public ScheduleHasReservationsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
