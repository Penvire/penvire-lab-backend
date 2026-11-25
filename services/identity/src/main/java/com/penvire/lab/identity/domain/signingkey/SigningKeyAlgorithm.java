package com.penvire.lab.identity.domain.signingkey;

public enum SigningKeyAlgorithm {
  ML_DSA_65("ML-DSA-65", "ML-DSA"),
  RS256("RS256", "RSA");

  private final String joseName;
  private final String keyFactoryAlgorithm;

  SigningKeyAlgorithm(String joseName, String keyFactoryAlgorithm) {
    this.joseName = joseName;
    this.keyFactoryAlgorithm = keyFactoryAlgorithm;
  }

  public String joseName() {
    return joseName;
  }

  /** The JCA {@code KeyFactory} name that reads this algorithm's encoded keys. */
  public String keyFactoryAlgorithm() {
    return keyFactoryAlgorithm;
  }
}
