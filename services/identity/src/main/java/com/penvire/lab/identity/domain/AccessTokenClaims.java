package com.penvire.lab.identity.domain;

import java.time.Instant;

public record AccessTokenClaims(
    String issuer,
    String subject,
    String clientId,
    String audience,
    String tokenId,
    Instant issuedAt,
    Instant notBefore,
    Instant expiresAt) {
  public AccessTokenClaims {
    if (issuer.isBlank()
        || subject.isBlank()
        || clientId.isBlank()
        || audience.isBlank()
        || tokenId.isBlank()) {
      throw new IllegalArgumentException("Access-token identifiers must not be blank");
    }

    if (!expiresAt.isAfter(issuedAt) || !expiresAt.isAfter(notBefore)) {
      throw new IllegalArgumentException(
          "Access-token expiration must be after issued-at and not-before");
    }
  }
}
