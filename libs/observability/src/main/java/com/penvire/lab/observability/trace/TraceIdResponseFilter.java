package com.penvire.lab.observability.trace;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@ApplicationScoped
public class TraceIdResponseFilter implements ContainerResponseFilter {

  private final TraceContextProvider traces;

  public TraceIdResponseFilter(TraceContextProvider traces) {
    this.traces = traces;
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    traces
        .currentTraceId()
        .ifPresent(traceId -> responseContext.getHeaders().putSingle("X-Trace-Id", traceId));
  }
}
