package com.penvire.lab.fga;

/**
 * Unchecked failure returned by OpenFGA for non-availability errors (for example HTTP 4xx
 * validation errors, unknown types or relations, or invalid client configuration).
 */
public class FgaException extends RuntimeException {

  public FgaException(String message) {
    super(message);
  }

  public FgaException(String message, Throwable cause) {
    super(message, cause);
  }
}
