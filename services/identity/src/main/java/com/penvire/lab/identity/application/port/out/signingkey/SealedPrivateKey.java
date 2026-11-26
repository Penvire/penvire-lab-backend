package com.penvire.lab.identity.application.port.out.signingkey;

public final class SealedPrivateKey {

  private final String kekId;
  private final byte[] nonce;
  private final byte[] ciphertext;

  public SealedPrivateKey(String kekId, byte[] nonce, byte[] ciphertext) {
    this.kekId = kekId;
    this.nonce = nonce.clone();
    this.ciphertext = ciphertext.clone();
  }

  public String kekId() {
    return kekId;
  }

  public byte[] nonce() {
    return nonce.clone();
  }

  public byte[] ciphertext() {
    return ciphertext.clone();
  }

  @Override
  public String toString() {
    return "SealedPrivateKey[" + kekId + "]";
  }
}
