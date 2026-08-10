package com.deportlink.deportlink.exception;

public class OwnerNotBelongsToClubException extends RuntimeException {
    public OwnerNotBelongsToClubException(String message) {
        super(message);
    }
}
