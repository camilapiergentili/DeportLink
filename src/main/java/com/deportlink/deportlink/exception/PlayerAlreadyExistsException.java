package com.deportlink.deportlink.exception;

public class PlayerAlreadyExistsException extends RuntimeException {
    public PlayerAlreadyExistsException(String message) {
        super(message);
    }
}
