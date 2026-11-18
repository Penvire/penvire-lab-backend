package com.penvire.lab.jwt;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record JwtValidationPolicy(
    String issuer,
    Set<String> audiences,
    JwtType type,
    Set<String> allowedAlgorithms,
    Duration clockSkew
) {
  public JwtValidationPolicy {
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(clockSkew, "clock skew");

    audiences = Set.copyOf(audiences);
    allowedAlgorithms = Set.copyOf(allowedAlgorithms);

    if (issuer.isBlank() || audiences.isEmpty()) {
      throw new IllegalArgumentException("Issuer and audience must not be blank");
    }

    if (allowedAlgorithms.isEmpty()) {
      throw new IllegalArgumentException("At least one algorithm must be allowed");
    }

    if (clockSkew.isNegative()) {
      throw new IllegalArgumentException("Clock skew must not be negative");
    }
  }
}
