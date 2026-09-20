package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class ScheduleNotFoundException extends BusinessException {
    public ScheduleNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
