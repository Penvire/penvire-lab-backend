package com.penvire.lab.identity.domain.signingkey;

import java.security.PrivateKey;

public record ActiveSigningKey(SigningKey key, PrivateKey privateKey) {

  @Override
  public String toString() {
    return "ActiveSigningKey[" + key.kid() + "]";
  }
}
