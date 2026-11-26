package com.penvire.lab.security.config;

import com.penvire.lab.jwt.validation.JwtType;
import com.penvire.lab.jwt.validation.JwtValidationPolicy;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

@ConfigMapping(prefix = "penvire.security")
public interface SecurityConfig {
  String issuer();

  Set<String> audiences();

  @WithDefault("ML-DSA-65,RS256")
  Set<String> allowedAlgorithms();

  @WithDefault("PT30S")
  Duration clockSkew();

  URI jwksUri();

  @WithDefault("PT5M")
  Duration jwksCacheTtl();

  @WithDefault("PT5S")
  Duration jwksRefreshCooldown();

  @WithDefault("PT3S")
  Duration jwksTimeout();

  default JwtValidationPolicy toPolicy() {
    return new JwtValidationPolicy(
        issuer(), audiences(), Set.of(JwtType.ACCESS), allowedAlgorithms(), clockSkew());
  }
}
