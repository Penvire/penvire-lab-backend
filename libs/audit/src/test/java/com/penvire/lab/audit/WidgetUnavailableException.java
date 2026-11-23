package com.penvire.lab.audit;

/** An error with a known reason stopped the action. */
class WidgetUnavailableException extends RuntimeException implements AuditReason {

  private static final long serialVersionUID = 1L;

  WidgetUnavailableException() {
    super("the widget store is down");
  }

  @Override
  public String auditReason() {
    return "widget_store_unavailable";
  }
}
