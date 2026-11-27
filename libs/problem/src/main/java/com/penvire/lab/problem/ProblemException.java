package com.penvire.lab.problem;

import org.jspecify.annotations.Nullable;

/**
 * A failure that reaches HTTP clients as problem details (RFC 9457). The exception names its {@link
 * ProblemType}; {@link ProblemExceptionMapper} renders it. The message goes to logs only.
 */
public abstract class ProblemException extends RuntimeException {

  private final ProblemType type;
  private final ProblemOccurrence occurrence;

  protected ProblemException(ProblemType type, String message) {
    this(type, ProblemOccurrence.NONE, message, null);
  }

  protected ProblemException(ProblemType type, String message, @Nullable Throwable cause) {
    this(type, ProblemOccurrence.NONE, message, cause);
  }

  protected ProblemException(ProblemType type, ProblemOccurrence occurrence, String message) {
    this(type, occurrence, message, null);
  }

  protected ProblemException(
      ProblemType type, ProblemOccurrence occurrence, String message, @Nullable Throwable cause) {
    super(message, cause);
    this.type = type;
    this.occurrence = occurrence;
  }

  public ProblemType type() {
    return type;
  }

  public final ProblemOccurrence occurrence() {
    return occurrence;
  }
}
