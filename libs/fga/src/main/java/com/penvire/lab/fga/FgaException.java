package com.penvire.lab.fga;

import com.penvire.lab.problem.ProblemException;
import com.penvire.lab.problem.ProblemKind;
import com.penvire.lab.problem.ProblemType;
import org.jspecify.annotations.Nullable;

/**
 * Unchecked failure returned by OpenFGA for non-availability errors (for example HTTP 4xx
 * validation errors, unknown types or relations, or invalid client configuration). Clients see
 * {@code fga.error} without the message.
 */
public class FgaException extends ProblemException {

  static final ProblemType ERROR =
      new ProblemType("fga.error", ProblemKind.INTERNAL, "Authorization error");

  public FgaException(String message) {
    super(ERROR, message);
  }

  public FgaException(String message, Throwable cause) {
    super(ERROR, message, cause);
  }

  public FgaException(ProblemType type, String message, @Nullable Throwable cause) {
    super(type, message, cause);
  }
}
