package com.penvire.lab.jwt.jwk;

public class JwksParseException extends RuntimeException {
  public JwksParseException(String message) {
    super(message);
  }

  public JwksParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
