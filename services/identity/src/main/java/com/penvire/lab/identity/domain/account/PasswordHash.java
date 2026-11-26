package com.penvire.lab.identity.domain.account;

public record PasswordHash(String value) {
  public PasswordHash {
    if (value.isBlank()) {
      throw new IllegalArgumentException("Invalid password hash");
    }

    if (value.length() < 8) {
      throw new IllegalArgumentException("Password hash must be at least 8 characters long");
    }
  }

  @Override
  public String toString() {
    return "PasswordHash[REDACTED]";
  }
}
