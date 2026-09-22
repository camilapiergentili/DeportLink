package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** El Player ya tiene un ClassEnrollment activo para ese ClassSlot. */
public class PlayerAlreadyEnrolledException extends BusinessException {
    public PlayerAlreadyEnrolledException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
