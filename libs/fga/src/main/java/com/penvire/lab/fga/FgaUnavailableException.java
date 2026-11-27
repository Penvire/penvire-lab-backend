package com.penvire.lab.fga;

/**
 * Raised when OpenFGA is unreachable, times out, rate-limits (HTTP 429), or returns a server error
 * (HTTP 5xx). Protected endpoints fail closed with {@code 503 Service Unavailable} and never fail
 * open (ADR 0010).
 */
public class FgaUnavailableException extends FgaException {

  public FgaUnavailableException(String message) {
    super(message);
  }

  public FgaUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
