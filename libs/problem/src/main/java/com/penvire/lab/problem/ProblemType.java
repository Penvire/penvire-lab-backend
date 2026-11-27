package com.penvire.lab.problem;

import java.util.regex.Pattern;

/**
 * A documented kind of failure, rendered as the problem type {@code urn:penvire:problem:<code>}.
 * Declare each one once, as a constant in the module that raises it.
 *
 * @param code {@code <module>.<name>} in lowercase kebab case, for example {@code fga.unavailable}
 * @param kind decides the HTTP status
 * @param title the same for every occurance; put what varies in {@link ProblemOccurrence}
 */
public record ProblemType(String code, ProblemKind kind, String title) {

  private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*");

  public ProblemType {
    if (!CODE.matcher(code).matches()) {
      throw new IllegalArgumentException(
          "Problem code must be <module>.<name> in lowercase kebab case: " + code);
    }

    if (title.isBlank()) {
      throw new IllegalArgumentException("Problem title must not be blank: " + title);
    }
  }
}
