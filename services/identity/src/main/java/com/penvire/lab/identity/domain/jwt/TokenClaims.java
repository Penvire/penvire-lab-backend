package com.penvire.lab.identity.domain.jwt;

import java.time.Instant;

public sealed interface TokenClaims permits AccessTokenClaims {
  String issuer();

  String subject();

  String audience();

  String tokenId();

  Instant issuedAt();

  Instant expiresAt();
}
