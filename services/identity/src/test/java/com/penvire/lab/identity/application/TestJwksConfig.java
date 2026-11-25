package com.penvire.lab.identity.application;

import com.penvire.lab.identity.config.JwksConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.time.Duration;
import java.util.Set;

/** ADR 0007's rotation: 30-day signing periods, the next key two days ahead. */
record TestJwksConfig(
    Set<SigningKeyAlgorithm> algorithms, Duration signingPeriod, Duration leadTime)
    implements JwksConfig {

  TestJwksConfig(Set<SigningKeyAlgorithm> algorithms) {
    this(algorithms, Duration.ofDays(30), Duration.ofDays(2));
  }
}
