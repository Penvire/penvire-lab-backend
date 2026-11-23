package com.penvire.lab.audit;

/** A subclass of a refusal, so an action listing {@link WidgetRefusedException} matches it too. */
class WidgetLimitReachedException extends WidgetRefusedException {

  private static final long serialVersionUID = 1L;

  WidgetLimitReachedException() {
    super("widget_limit_reached");
  }
}
