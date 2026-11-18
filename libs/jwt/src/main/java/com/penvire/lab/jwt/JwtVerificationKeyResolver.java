package com.penvire.lab.jwt;

import java.security.PublicKey;

@FunctionalInterface
public interface JwtVerificationKeyResolver {
  PublicKey resolve(String trustedIssuer, String keyId, String algorithm, JwtType type);
}
