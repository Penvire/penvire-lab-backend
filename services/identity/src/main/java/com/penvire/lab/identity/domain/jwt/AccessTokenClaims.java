package com.penvire.lab.identity.domain.jwt;

import static com.penvire.lab.identity.domain.jwt.ClaimRules.requireIdentifier;

import java.time.Instant;

public record AccessTokenClaims(
    String issuer,
    String subject,
    String clientId,
    String audience,
    String tokenId,
    Instant issuedAt,
    Instant notBefore,
    Instant expiresAt)
    implements TokenClaims {

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
}
