package com.penvire.lab.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TraceIdProblemPostProcessorTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Test
  void addsTheTraceIdWithoutChangingProblemFieldsOrHeaders() {
    var problem = problem();
    var processor = new TraceIdProblemPostProcessor(() -> Optional.of(TRACE_ID));

    var result = processor.apply(problem, context(problem));

    assertEquals(TRACE_ID, result.getParameters().get("traceId"));
    assertEquals(URI.create("urn:penvire:problem:test-conflict"), result.getType());
    assertEquals(409, result.getStatusCode());
    assertEquals("Conflict", result.getTitle());
    assertEquals("The requested change conflicts with current state.", result.getDetail());
    assertEquals(URI.create("/test/problems/conflict"), result.getInstance());
    assertEquals("existing-value", result.getParameters().get("existing"));
    assertEquals(Map.of("Retry-After", "30"), result.getHeaders());
    assertFalse(problem.getParameters().containsKey("traceId"));
  }

  @Test
  void leavesTheProblemUnchangedWhenThereIsNoActiveTrace() {
    var problem = problem();
    var processor = new TraceIdProblemPostProcessor(Optional::empty);

    var result = processor.apply(problem, context(problem));

    assertFalse(result.getParameters().containsKey("traceId"));
    assertEquals(problem.getType(), result.getType());
    assertEquals(problem.getTitle(), result.getTitle());
    assertEquals(problem.getStatusCode(), result.getStatusCode());
    assertEquals(problem.getDetail(), result.getDetail());
    assertEquals(problem.getInstance(), result.getInstance());
    assertEquals(problem.getParameters(), result.getParameters());
    assertEquals(problem.getHeaders(), result.getHeaders());
  }

  @Test
  void replacesAStaleTraceIdWithTheCurrentTraceId() {
    var problem = HttpProblem.builder(problem()).with("traceId", "stale-id").build();
    var processor = new TraceIdProblemPostProcessor(() -> Optional.of(TRACE_ID));

    var result = processor.apply(problem, context(problem));

    assertEquals(TRACE_ID, result.getParameters().get("traceId"));
    assertEquals("stale-id", problem.getParameters().get("traceId"));
  }

  private static HttpProblem problem() {
    return HttpProblem.builder()
        .withType(URI.create("urn:penvire:problem:test-conflict"))
        .withStatus(409)
        .withTitle("Conflict")
        .withDetail("The requested change conflicts with current state.")
        .withInstance(URI.create("/test/problems/conflict"))
        .with("existing", "existing-value")
        .withHeader("Retry-After", "30")
        .build();
  }

  private static ProblemContext context(HttpProblem problem) {
    return ProblemContext.of(problem, "/test/problems/conflict");
  }
}
