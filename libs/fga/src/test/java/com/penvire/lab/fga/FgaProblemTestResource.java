package com.penvire.lab.fga;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/test/fga")
@Produces(MediaType.APPLICATION_JSON)
public class FgaProblemTestResource {

  @GET
  @Path("/unavailable")
  public void unavailable() {
    throw new FgaUnavailableException("OpenFGA check unavailable (HTTP 503)");
  }

  @GET
  @Path("/error")
  public void error() {
    throw new FgaException("OpenFGA check failed (HTTP 400): unknown relation");
  }
}
