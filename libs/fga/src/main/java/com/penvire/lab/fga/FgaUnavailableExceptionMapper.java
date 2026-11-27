package com.penvire.lab.fga;

import io.quarkiverse.httpproblem.ExceptionMapperBase;
import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.PostProcessorsRegistry;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps {@link FgaUnavailableException} to {@code 503 Service Unavailable} problem details (ADR
 * 0010). Uses a fixed title without forwarding the exception message so internal HTTP statuses or
 * operation details are never leaked. Plain {@link FgaException} stays unmapped (HTTP 500).
 */
@Provider
@Priority(Priorities.USER)
public final class FgaUnavailableExceptionMapper
    extends ExceptionMapperBase<FgaUnavailableException>
    implements ExceptionMapper<FgaUnavailableException> {

  @Inject
  public FgaUnavailableExceptionMapper(PostProcessorsRegistry postProcessorsRegistry) {
    super(postProcessorsRegistry);
  }

  @Override
  protected HttpProblem toProblem(FgaUnavailableException exception) {
    return HttpProblem.builder()
        .withStatus(Response.Status.SERVICE_UNAVAILABLE)
        .withTitle("Authorization service unavailable")
        .build();
  }
}
