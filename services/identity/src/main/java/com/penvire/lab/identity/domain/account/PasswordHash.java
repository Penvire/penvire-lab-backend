package com.penvire.lab.identity.domain.account;

public record PasswordHash(String value) {
  public PasswordHash {
    if (value.isBlank()) {
      throw new IllegalArgumentException("Invalid password hash");
    }
  }

  @Override
  public String toString() {
    return "PasswordHash[REDACTED]";
  }
}
