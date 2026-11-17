package com.penvire.lab.observability.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenTelemetryTraceContextProviderTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";

  private final TraceContextProvider traces = new OpenTelemetryTraceContextProvider();

  @Test
  void returnsEmptyWithoutAnActiveSpan() {
    try (var ignored = Context.root().makeCurrent()) {
      assertTrue(traces.currentTraceId().isEmpty());
    }
  }

  @Test
  void returnsTheActiveTraceId() {
    var context =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    try (var ignored = Span.wrap(context).makeCurrent()) {
      assertEquals(Optional.of(TRACE_ID), traces.currentTraceId());
    }
  }

  @Test
  void returnsTheTraceIdEvenWhenTheSpanIsNotSampled() {
    var context =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());
    try (var ignored = Span.wrap(context).makeCurrent()) {
      assertEquals(Optional.of(TRACE_ID), traces.currentTraceId());
    }
  }

  @Test
  void restoresTheOuterTraceAfterANestedScopeCloses() {
    var outer =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    var innerTraceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    var inner =
        SpanContext.create(innerTraceId, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());

    try (var ignored = Span.wrap(outer).makeCurrent()) {
      try (var nested = Span.wrap(inner).makeCurrent()) {
        assertEquals(Optional.of(innerTraceId), traces.currentTraceId());
      }
      assertEquals(Optional.of(TRACE_ID), traces.currentTraceId());
    }
  }
}
