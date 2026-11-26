package com.penvire.lab.identity.domain.account;

import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, Email email, Username username, boolean enabled, Instant createdAt) {
  /** Opaque OIDC {@code sub} claim. */
  public String subject() {
    return id.toString();
  }

  public Account disable() {
    return new Account(id, email, username, false, createdAt);
  }

  public Account enable() {
    return new Account(id, email, username, true, createdAt);
  }
}
