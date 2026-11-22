package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;

@FunctionalInterface
public interface AccessTokenSigner {
  String sign(AccessTokenClaims claims);
}
