package com.penvire.lab.identity.domain.signingkey;

public enum SigningKeyAlgorithm {
  ML_DSA_65("ML-DSA-65"),
  RS256("RS256");

  private final String joseName;

  SigningKeyAlgorithm(String joseName) {
    this.joseName = joseName;
  }

  public String joseName() {
    return joseName;
  }
}
