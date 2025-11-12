package com.deportlink.DeportLink.exception;

public class BranchNotApprovedException extends RuntimeException {
  public BranchNotApprovedException(String message) {
    super(message);
  }
}
