package com.penvire.lab.problem;

import io.quarkiverse.httpproblem.ExceptionMapperBase;
import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.PostProcessorsRegistry;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

/**
 * Renders every {@link ProblemException} as problem details. This is the only place where a {@link
 * ProblemKind} becomes an HTTP status. Post-processors still run, so the trace id is added.
 */
@Provider
@Priority(Priorities.USER)
public final class ProblemExceptionMapper extends ExceptionMapperBase<ProblemException>
    implements ExceptionMapper<ProblemException> {

  private static final String TYPE_PREFIX = "urn:penvire:problem:";

  public ProblemExceptionMapper(PostProcessorsRegistry postProcessorsRegistry) {
    super(postProcessorsRegistry);
  }

  @Override
  protected HttpProblem toProblem(ProblemException exception) {
    var type = exception.type();
    var problem =
        HttpProblem.builder()
            .withType(URI.create(TYPE_PREFIX + type.code()))
            .withStatus(status(type.kind()))
            .withDetail(exception.occurrence().detail())
            .withTitle(type.title());

    var detail = exception.occurrence().detail();
    if (detail != null) {
      problem.withDetail(detail);
    }

    exception.occurrence().properties().forEach(problem::with);

    var retryAfter = exception.occurrence().retryAfter();
    if (retryAfter != null) {
      problem.withHeader("Retry-After", Math.ceilDiv(retryAfter.toMillis(), 1000));
    }

    return problem.build();
  }

  private static int status(ProblemKind kind) {
    return switch (kind) {
      case INVALID -> 422; // Matches quarkus.http-problem.constraint-violation.status
      case NOT_FOUND -> 404;
      case CONFLICT -> 409;
      case UNAVAILABLE -> 503;
      case INTERNAL -> 500;
    };
  }
}
