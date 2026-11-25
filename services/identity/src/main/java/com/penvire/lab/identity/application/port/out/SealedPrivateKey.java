package com.penvire.lab.identity.application.port.out;

public record SealedPrivateKey(String kekId, byte[] nonce, byte[] ciphertext) {

  @Override
  public String toString() {
    return "SealedPrivateKey[" + kekId + "]";
  }
}
