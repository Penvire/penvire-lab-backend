package com.penvire.lab.jwt.validation;

public class JwtVerificationException extends RuntimeException {

  public JwtVerificationException(String message) {
    super(message);
  }

  public JwtVerificationException(String message, Throwable cause) {
    super(message, cause);
  }
}
