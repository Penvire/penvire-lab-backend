package com.penvire.lab.problem;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;

@Path("/test/problems")
@Produces(MediaType.APPLICATION_JSON)
public class ProblemTestResource {

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

  public record Request(@NotBlank String name) {}
}
