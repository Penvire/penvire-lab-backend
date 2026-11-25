package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores signing keys with their private part protected (ADR 0007, ADR 0009) */
public interface SigningKeyPersistencePort {

  /** Stores a new key together with its private part */
  void insert(SigningKey key, PrivateKey privateKey);

  /** Retires the current stored key, keeping it published for {@code retention}. */
  void retire(UUID id, Duration retention);

  /** Revokes the current stored key immediately and erases its private part. */
  void revoke(UUID id);

  /** The keys in the JWKS at {@code now}; tampered rows are left out. */
  List<SigningKey> findPublishedAt(Instant now);

  /** The key that signs with {@code algorithm} at {@code now}, with its private part. */
  Optional<ActiveSigningKey> findSignerAt(SigningKeyAlgorithm algorithm, Instant now);

  /** Erases the private parts of keys that no longer sign. Returns how many were erased. */
  int erasePrivateKeysAt(Instant now);

  /** Deletes keys past their publication. Returns how many were deleted. */
  int deleteExpiredAt(Instant now);

  /** Holds the rotation lock until the transaction ends. */
  void lockRotation();
}
