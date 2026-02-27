package com.deportlink.deportlink.exception;

public class CancellationTimeExceededException extends RuntimeException {
    public CancellationTimeExceededException(String message) {
        super(message);
    }
}
