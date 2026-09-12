package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ScheduleAlreadyExistsException extends BusinessException {
    public ScheduleAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
