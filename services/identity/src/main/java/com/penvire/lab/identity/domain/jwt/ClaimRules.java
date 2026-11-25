package com.penvire.lab.identity.domain.jwt;

class ClaimRules {
  private static final int MAX_IDENTIFIER_LENGTH = 4_096;

  static void requireIdentifier(String name, String value) {
    if (value.isBlank() || value.length() > MAX_IDENTIFIER_LENGTH) {
      throw new IllegalArgumentException("Invalid token claim: " + name);
    }
  }
}
