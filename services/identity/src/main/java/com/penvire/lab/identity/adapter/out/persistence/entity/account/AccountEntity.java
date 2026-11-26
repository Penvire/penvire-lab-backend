package com.penvire.lab.identity.adapter.out.persistence.entity.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "accounts")
public class AccountEntity {

  /** Opaque subject identifier ({@code sub} claim), assigned by {@code IdGenerator}. */
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /** MUST be normalized */
  @Column(name = "email", nullable = false, unique = true)
  private String email;

  /** MUST be normalized */
  @Column(name = "username", nullable = false, unique = true)
  private String username;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
