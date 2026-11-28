package com.penvire.lab.fga;

import com.penvire.lab.problem.ProblemKind;
import com.penvire.lab.problem.ProblemType;

/**
 * Raised when OpenFGA is unreachable, times out, rate-limits (HTTP 429), or returns a server error
 * (HTTP 5xx). Protected endpoints fail closed with {@code 503 Service Unavailable} and never fail
 * open (ADR 0010).
 */
public class FgaUnavailableException extends FgaException {

  static final ProblemType UNAVAILABLE =
      new ProblemType(
          "fga.unavailable", ProblemKind.UNAVAILABLE, "Authorization service unavailable");

  public FgaUnavailableException(String message) {
    super(UNAVAILABLE, message, null);
  }

  public FgaUnavailableException(String message, Throwable cause) {
    super(UNAVAILABLE, message, cause);
  }
}
