package com.penvire.lab.identity.adapter.out.persistence.entity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "password_credentials")
public class PasswordCredentialEntity {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  @Nullable
  private Instant revokedAt;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;
}
