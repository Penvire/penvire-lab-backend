package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.fail;

import com.penvire.lab.identity.config.JwtConfig;
import java.time.Duration;

record TestJwtConfig(Duration accessTokenLifetime) implements JwtConfig {
  @Override
  public AccessToken accessToken() {
    return this::accessTokenLifetime;
  }

  @Override
  public RefreshToken refreshToken() {
    return fail("Access-token issuance must not read refresh-token settings");
  }
}
