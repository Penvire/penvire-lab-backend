package com.penvire.lab.problem;

/**
 * What went wrong, independent of any transport. {@link ProblemExceptionMapper} turns each kind
 * into an HTTP status; business code never names one.
 *
 * <p>Refusals are not a kind: authorization exceptions extend Quarkus' {@link ForbiddenException}
 * so that {@code @Audited} records them as {@code DENIED} (ADR 0008).
 */
public enum ProblemKind {

  /** The request is well-formed but breaks a rule. */
  INVALID,

  /** What the request refers to does not exist. */
  NOT_FOUND,

  /** The request conflicts with current state */
  CONFLICT,

  /** A dependency is down; retrying later may succeed. */
  UNAVAILABLE,

  /** A failure the caller cannot fix */
  INTERNAL
}
