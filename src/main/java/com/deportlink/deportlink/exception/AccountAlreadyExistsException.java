package com.deportlink.deportlink.exception;
import org.springframework.http.HttpStatus;
public class AccountAlreadyExistsException extends BusinessException {
    public AccountAlreadyExistsException(String message) { super(message, HttpStatus.CONFLICT); }
}
