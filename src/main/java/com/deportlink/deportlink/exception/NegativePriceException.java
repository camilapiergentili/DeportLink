package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class NegativePriceException extends BusinessException {
    public NegativePriceException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
