package com.penvire.lab.identity.domain.account;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PasswordCredential(
    UUID id,
    UUID accountId,
    PasswordHash passwordHash,
    Instant createdAt,
    @Nullable Instant revokedAt) {

  public PasswordCredential {
    if (revokedAt != null && revokedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("revokedAt must not be before createdAt");
    }
  }

  public static PasswordCredential active(
      UUID id, UUID accountId, PasswordHash passwordHash, Instant createdAt) {
    return new PasswordCredential(id, accountId, passwordHash, createdAt, null);
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public PasswordCredential revoke(Instant revokedAt) {
    if (!isActive()) {
      throw new IllegalStateException("PasswordCredential is already revoked");
    }
    return new PasswordCredential(id, accountId, passwordHash, createdAt, revokedAt);
  }
}
