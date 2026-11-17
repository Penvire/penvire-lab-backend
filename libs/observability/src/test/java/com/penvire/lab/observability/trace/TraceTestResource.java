package com.penvire.lab.observability.trace;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/test/trace")
@Produces(MediaType.TEXT_PLAIN)
public class TraceTestResource {

  @GET
  public String success() {
    return "ok";
  }

  @GET
  @Path("/error")
  public Response error() {
    return Response.status(Response.Status.CONFLICT).entity("conflict").build();
  }

  @GET
  @Path("/existing-header")
  public Response existingHeader() {
    return Response.ok("ok").header("X-Trace-Id", "stale-id").build();
  }
}
