package com.penvire.lab.security;

public class JwksUnavailableException extends RuntimeException {
  public JwksUnavailableException(String message) {
    super(message);
  }

  public JwksUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
