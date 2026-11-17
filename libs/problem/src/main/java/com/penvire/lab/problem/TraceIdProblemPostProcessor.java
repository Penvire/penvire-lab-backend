package com.penvire.lab.problem;

import com.penvire.lab.observability.trace.TraceContextProvider;
import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TraceIdProblemPostProcessor implements ProblemPostProcessor {
  private final TraceContextProvider traces;

  public TraceIdProblemPostProcessor(TraceContextProvider traces) {
    this.traces = traces;
  }

  @Override
  public HttpProblem apply(HttpProblem problem, ProblemContext context) {
    return traces
        .currentTraceId()
        .map(traceId -> HttpProblem.builder(problem).with("traceId", traceId).build())
        .orElse(problem);
  }

  @Override
  public int priority() {
    return 100;
  }
}
