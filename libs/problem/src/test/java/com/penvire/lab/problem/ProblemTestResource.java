package com.penvire.lab.problem;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.time.Duration;

@Path("/test/problems")
@Produces(MediaType.APPLICATION_JSON)
public class ProblemTestResource {

  static final String LOG_ONLY_MESSAGE = "internal-log-only-message";

  @GET
  @Path("/conflict")
  public void conflict() {
    throw HttpProblem.builder()
        .withType(URI.create("urn:penvire:problem:test-conflict"))
        .withStatus(409)
        .withTitle("Conflict")
        .withDetail("The requested change conflicts with current state.")
        .withHeader("Retry-After", "30")
        .build();
  }

  @GET
  @Path("/unexpected")
  public void unexpected() {
    throw new IllegalStateException("internal-database-connection-detail");
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Request validate(@Valid Request request) {
    return request;
  }

  @GET
  @Path("/exception")
  public void exception() {
    throw new TestProblemException(ProblemKind.CONFLICT);
  }

  @GET
  @Path("/exception-with-detail")
  public void exceptionWithDetail() {
    throw new TestProblemException(
        ProblemKind.CONFLICT, ProblemOccurrence.NONE.withDetail("The order was already shipped."));
  }

  @GET
  @Path("/exception-with-properties")
  public void exceptionWithProperties() {
    throw new TestProblemException(
        ProblemKind.CONFLICT, ProblemOccurrence.NONE.withProperty("currentVersion", 7));
  }

  @GET
  @Path("/exception-with-retry-after")
  public void exceptionWithRetryAfter() {
    throw new TestProblemException(
        ProblemKind.UNAVAILABLE, ProblemOccurrence.NONE.withRetryAfter(Duration.ofMillis(1500)));
  }

  @GET
  @Path("/kinds/{kind}")
  public void kind(@PathParam("kind") ProblemKind kind) {
    throw new TestProblemException(kind);
  }

  public record Request(@NotBlank String name) {}

  static final class TestProblemException extends ProblemException {
    TestProblemException(ProblemKind kind) {
      this(kind, ProblemOccurrence.NONE);
    }

    TestProblemException(ProblemKind kind, ProblemOccurrence occurrence) {
      super(new ProblemType("test.problem", kind, "Test problem"), occurrence, LOG_ONLY_MESSAGE);
    }
  }
}
