package com.penvire.lab.audit;

/** A rule refused the action; actions list it in {@code deniedOn}. */
class WidgetRefusedException extends RuntimeException implements AuditReason {

  private static final long serialVersionUID = 1L;

  private final String reason;

  WidgetRefusedException(String reason) {
    super("refused: the caller's secret is s3cr3t");
    this.reason = reason;
  }

  @Override
  public String auditReason() {
    return reason;
  }
}
