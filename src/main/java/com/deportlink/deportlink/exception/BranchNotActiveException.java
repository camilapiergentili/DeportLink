package com.deportlink.deportlink.exception;

public class BranchNotActiveException extends RuntimeException {
    public BranchNotActiveException(String message) {
        super(message);
    }
}
