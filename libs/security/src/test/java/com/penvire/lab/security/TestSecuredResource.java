package com.penvire.lab.security;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.stream.Collectors;

@Path("/test")
@Produces(MediaType.TEXT_PLAIN)
public class TestSecuredResource {

  @Inject SecurityIdentity identity;

  @GET
  @Path("/protected")
  @Authenticated
  public String protectedEndpoint() {
    BearerPrincipal principal = (BearerPrincipal) identity.getPrincipal();
    String scopes = principal.scopes().stream().sorted().collect(Collectors.joining(","));
    String audiences = principal.audiences().stream().sorted().collect(Collectors.joining(","));
    return String.join(
        "|",
        principal.getName(),
        principal.issuer(),
        principal.clientId(),
        principal.tokenId(),
        audiences,
        scopes);
  }

  @GET
  @Path("/public")
  @PermitAll
  public String publicEndpoint() {
    return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
  }
}
