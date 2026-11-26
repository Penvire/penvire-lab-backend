package com.penvire.lab.jwt.validation;

import java.security.PublicKey;

@FunctionalInterface
public interface JwtVerificationKeyResolver {
  /**
   * Returns a key from the supplied trust configuration only if the issuer, key ID, exact
   * algorithm, and allowed JWT type match. Token-supplied key material and URLs do not establish
   * trust.
   *
   * @throws JwtVerificationException if no configured key matches
   */
  PublicKey resolve(String trustedIssuer, String keyId, String algorithm, JwtType type);
}
