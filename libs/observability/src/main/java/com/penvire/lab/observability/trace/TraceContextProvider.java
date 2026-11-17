package com.penvire.lab.observability.trace;

import java.util.Optional;

/** Provides correlation data from the active trace. */
public interface TraceContextProvider {

  /**
   * Get the current correlation traceId.
   *
   * @return current correlation traceId
   */
  Optional<String> currentTraceId();
}
