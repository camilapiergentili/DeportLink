package com.deportlink.deportlink.exception;

public class SportNotFoundException extends RuntimeException {
    public SportNotFoundException(String message) {
        super(message);
    }
}
