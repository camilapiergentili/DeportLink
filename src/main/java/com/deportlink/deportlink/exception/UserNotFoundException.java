package com.deportlink.deportlink.exception;

public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
