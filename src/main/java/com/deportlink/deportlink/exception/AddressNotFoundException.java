package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

public class AddressNotFoundException extends BusinessException {
    public AddressNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
