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
  private static final int MAX_IDENTIFIER_LENGTH = 4_096;

  public AccessTokenClaims {
    requireIdentifier("iss", issuer);
    requireIdentifier("sub", subject);
    requireIdentifier("client_id", clientId);
    requireIdentifier("aud", audience);
    requireIdentifier("jti", tokenId);

    if (!expiresAt.isAfter(issuedAt) || !expiresAt.isAfter(notBefore)) {
      throw new IllegalArgumentException(
          "Access-token expiration must be after issued-at and not-before");
    }
  }

  private static void requireIdentifier(String name, String value) {
    if (value.isBlank() || value.length() > MAX_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException("Invalid access-token claim: " + name);
    }
  }
}
