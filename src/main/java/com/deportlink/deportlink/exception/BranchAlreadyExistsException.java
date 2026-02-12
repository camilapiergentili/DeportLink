package com.deportlink.deportlink.exception;

public class BranchAlreadyExistsException extends RuntimeException {
    public BranchAlreadyExistsException(String message) {
        super(message);
    }
}
