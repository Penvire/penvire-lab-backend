package com.penvire.lab.identity.config;

import io.smallrye.config.ConfigMapping;
import java.time.Duration;
import java.time.Period;

@ConfigMapping(prefix = "penvire.jwt")
public interface JwtConfig {
  AccessToken accessToken();

  RefreshToken refreshToken();

  interface AccessToken {
    Duration lifetime();
  }

  interface RefreshToken {
    Duration idleTimeout();

    Period absoluteLifetime();
  }
}
