package com.penvire.lab.audit;

/** How an audited action ended; the same symbols as the contract's {@code AuditOutcome}. */
enum Outcome {
  SUCCESS,
  DENIED,
  FAILED
}
