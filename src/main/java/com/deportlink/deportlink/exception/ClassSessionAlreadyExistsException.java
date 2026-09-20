package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** Ya existe una ClassSession para ese (classSlotId, day). */
public class ClassSessionAlreadyExistsException extends BusinessException {
    public ClassSessionAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
