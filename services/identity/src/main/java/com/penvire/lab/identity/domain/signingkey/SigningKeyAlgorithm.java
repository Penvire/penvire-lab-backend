package com.penvire.lab.identity.domain.signingkey;

public enum SigningKeyAlgorithm {
  ML_DSA_65("ML-DSA-65", "ML-DSA", "ML-DSA-65"),
  RS256("RS256", "RSA", "SHA256withRSA");

  private final String joseName;
  private final String keyFactoryAlgorithm;
  private final String signingName;

  SigningKeyAlgorithm(String joseName, String keyFactoryAlgorithm, String signingName) {
    this.joseName = joseName;
    this.keyFactoryAlgorithm = keyFactoryAlgorithm;
    this.signingName = signingName;
  }

  /**
   * The algorithm with JOSE name {@code joseName}, matched exactly (RFC 7515 §4.1.1).
   *
   * @throws IllegalArgumentException if no algorithm has that name
   */
  public static SigningKeyAlgorithm fromJoseName(String joseName) {
    for (var algorithm : values()) {
      if (algorithm.joseName.equals(joseName)) {
        return algorithm;
      }
    }
    throw new IllegalArgumentException("Unsupported JOSE algorithm: " + joseName);
  }

  public String joseName() {
    return joseName;
  }

  /** The JCA {@code KeyFactory} name that reads this algorithm's encoded keys. */
  public String keyFactoryAlgorithm() {
    return keyFactoryAlgorithm;
  }

  /** The JCA {@code Signature} name that signs and verifies with this algorithm. */
  public String signatureAlgorithm() {
    return signingName;
  }
}
