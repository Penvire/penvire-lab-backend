package com.penvire.lab.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class BearerAuthenticationMechanism implements HttpAuthenticationMechanism {

  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String authHeader = context.request().getHeader("Authorization");
    if (authHeader == null) {
      return Uni.createFrom().nullItem();
    }

    if (authHeader.equalsIgnoreCase("Bearer")) {
      return Uni.createFrom().failure(new AuthenticationFailedException("Empty bearer token"));
    }

    if (!startsWithBearer(authHeader)) {
      return Uni.createFrom().nullItem();
    }

    String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (rawToken.isEmpty()) {
      return Uni.createFrom().failure(new AuthenticationFailedException("Empty bearer token"));
    }

    TokenAuthenticationRequest request =
        new TokenAuthenticationRequest(new TokenCredential(rawToken, "bearer"));

    return identityProviderManager
        .authenticate(request)
        .onFailure(JwksUnavailableException.class)
        .transform(
            failure -> new HttpException(503, "Authentication service unavailable", failure));
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Set.of(TokenAuthenticationRequest.class);
  }

  @Override
  public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
    return Uni.createFrom()
        .item(new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "bearer"));
  }

  private static boolean startsWithBearer(String header) {
    return header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
  }
}
