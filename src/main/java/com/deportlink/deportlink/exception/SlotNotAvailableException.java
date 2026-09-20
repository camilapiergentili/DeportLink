package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class SlotNotAvailableException extends BusinessException {
    public SlotNotAvailableException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
