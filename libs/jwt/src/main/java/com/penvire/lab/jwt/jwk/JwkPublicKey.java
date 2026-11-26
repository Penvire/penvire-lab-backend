package com.penvire.lab.jwt.jwk;

import java.security.PublicKey;
import java.util.Objects;

public record JwkPublicKey(String keyId, String algorithm, PublicKey publicKey) {
  public JwkPublicKey {
    Objects.requireNonNull(keyId, "keyId");
    Objects.requireNonNull(algorithm, "algorithm");
    Objects.requireNonNull(publicKey, "publicKey");
    if (keyId.isBlank() || algorithm.isBlank()) {
      throw new IllegalArgumentException("keyId and algorithm must not be blank");
    }
  }
}
