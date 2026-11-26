package com.penvire.lab.identity.application.port.out.jwt;

import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;

@FunctionalInterface
public interface TokenSignerPort {
  /** Returns the compact JWS of {@code claims}, signed with {@code key}. */
  String sign(AccessTokenClaims claims, ActiveSigningKey key);
}
