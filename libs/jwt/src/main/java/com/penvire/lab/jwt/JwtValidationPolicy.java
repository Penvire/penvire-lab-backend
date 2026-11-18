package com.penvire.lab.jwt;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public record JwtValidationPolicy(
    String issuer,
    Set<String> audiences,
    Set<JwtType> types,
    Set<String> allowedAlgorithms,
    Duration clockSkew) {
  public JwtValidationPolicy {
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(clockSkew, "clock skew");

    audiences = Set.copyOf(audiences);
    allowedAlgorithms = Set.copyOf(allowedAlgorithms);
    types = Set.copyOf(types);

    if (issuer.isBlank() || audiences.isEmpty()) {
      throw new IllegalArgumentException("Issuer and audience must not be blank");
    }

    if (allowedAlgorithms.isEmpty()) {
      throw new IllegalArgumentException("At least one algorithm must be allowed");
    }

    if (types.isEmpty()) {
      throw new IllegalArgumentException("At least one type must be allowed");
    }

    if (clockSkew.isNegative()) {
      throw new IllegalArgumentException("Clock skew must not be negative");
    }
  }
}
