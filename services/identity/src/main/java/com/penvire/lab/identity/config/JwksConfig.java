package com.penvire.lab.identity.config;

import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Set;

@ConfigMapping(prefix = "penvire.jwks")
public interface JwksConfig {
  Set<SigningKeyAlgorithm> algorithms();

  Duration signingPeriod();

  Duration leadTime();

  @WithDefault("24h")
  Duration rotationInterval();

  @WithDefault("24h")
  Duration rotationDelay();
}
