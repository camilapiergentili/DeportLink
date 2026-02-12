package com.deportlink.deportlink.exception;

public class BranchNotApprovedException extends RuntimeException {
  public BranchNotApprovedException(String message) {
    super(message);
  }
}
