package com.penvire.lab.security;

import com.penvire.lab.jwt.validation.JwtValidator;
import com.penvire.lab.jwt.validation.JwtVerificationException;
import com.penvire.lab.jwt.validation.VerifiedJwt;
import com.penvire.lab.security.config.SecurityConfig;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JwtIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

  private final JwtValidator validator;
  private final SecurityConfig config;

  public JwtIdentityProvider(JwtValidator validator, SecurityConfig config) {
    this.validator = validator;
    this.config = config;
  }

  @Override
  public Class<TokenAuthenticationRequest> getRequestType() {
    return TokenAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      TokenAuthenticationRequest request, AuthenticationRequestContext context) {
    return context.runBlocking(() -> verifyAndBuildIdentity(request.getToken().getToken()));
  }

  private SecurityIdentity verifyAndBuildIdentity(String rawToken) {
    try {
      VerifiedJwt verifiedJwt = validator.validate(rawToken, config.toPolicy());
      BearerPrincipal principal = BearerPrincipal.from(verifiedJwt);
      return QuarkusSecurityIdentity.builder().setPrincipal(principal).build();
    } catch (JwtVerificationException e) {
      throw new AuthenticationFailedException("Invalid bearer token", e);
    }
  }
}
