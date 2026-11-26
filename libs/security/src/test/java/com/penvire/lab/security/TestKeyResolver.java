package com.penvire.lab.security;

import com.penvire.lab.jwt.validation.JwtType;
import com.penvire.lab.jwt.validation.JwtVerificationException;
import com.penvire.lab.jwt.validation.JwtVerificationKeyResolver;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TestKeyResolver implements JwtVerificationKeyResolver {

  private record KeyLookup(String issuer, String keyId, String algorithm, JwtType type) {}

  private final Map<KeyLookup, PublicKey> keys = new ConcurrentHashMap<>();
  private volatile boolean unavailable = false;

  public void register(
      String issuer, String keyId, String algorithm, JwtType type, PublicKey publicKey) {
    keys.put(new KeyLookup(issuer, keyId, algorithm, type), publicKey);
  }

  public void setUnavailable(boolean unavailable) {
    this.unavailable = unavailable;
  }

  public void clear() {
    keys.clear();
    unavailable = false;
  }

  @Override
  public PublicKey resolve(String trustedIssuer, String keyId, String algorithm, JwtType type) {
    if (unavailable) {
      throw new JwksUnavailableException("JWKS unavailable in test");
    }
    PublicKey key = keys.get(new KeyLookup(trustedIssuer, keyId, algorithm, type));
    if (key == null) {
      throw new JwtVerificationException("Unknown verification key");
    }
    return key;
  }
}
