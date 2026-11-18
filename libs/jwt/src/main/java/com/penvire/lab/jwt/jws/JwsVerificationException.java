package com.penvire.lab.jwt.jws;

/** Indicates that a JWS could not be verified. */
public class JwsVerificationException extends RuntimeException {

  public JwsVerificationException(String message) {
    super(message);
  }

  public JwsVerificationException(String message, Throwable cause) {
    super(message, cause);
  }
}
