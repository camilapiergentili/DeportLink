package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** El ClassSlot alcanzó su capacidad máxima de ClassEnrollment activos. */
public class ClassSlotFullException extends BusinessException {
    public ClassSlotFullException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
