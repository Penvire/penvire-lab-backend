package com.penvire.lab.audit;

/**
 * Implemented by exceptions that carry a machine-readable reason code for the audit trail. Whether
 * the exception is recorded, and as DENIED or FAILED, is decided by the action's {@link
 * Audited#deniedOn()} and {@link Audited#failedOn()}. The exception's message is never recorded.
 */
public interface AuditReason {

  /** A machine-readable reason code, for example {@code invalid_client}. */
  String auditReason();
}
