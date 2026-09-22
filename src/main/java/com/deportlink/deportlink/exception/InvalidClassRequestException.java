package com.deportlink.deportlink.exception;
import org.springframework.http.HttpStatus;
public class InvalidClassRequestException extends BusinessException {
    public InvalidClassRequestException(String message) { super(message, HttpStatus.BAD_REQUEST); }
}
