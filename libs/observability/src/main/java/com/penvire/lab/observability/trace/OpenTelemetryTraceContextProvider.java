package com.penvire.lab.observability.trace;

import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/** TraceContextProvider implementation for OpenTelemetry. */
@ApplicationScoped
public class OpenTelemetryTraceContextProvider implements TraceContextProvider {

  @Override
  public Optional<String> currentTraceId() {
    var context = Span.current().getSpanContext();
    return context.isValid() ? Optional.of(context.getTraceId()) : Optional.empty();
  }
}
