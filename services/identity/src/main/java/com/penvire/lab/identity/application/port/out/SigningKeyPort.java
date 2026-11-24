package com.penvire.lab.identity.application.port.out;

import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Stores signing keys with their private part protected (ADR 0007, ADR 0009) */
public interface SigningKeyPort {

  /** Stores a new key together with its private part */
  void insert(SigningKey key, PrivateKey privateKey);

  /** Stores a retired or revoked key; a revoked key loses its private part at once. */
  void update(SigningKey key);

  /** The keys iin the JWKS at {@code now}; tampered rows are left out. */
  List<SigningKey> findPublishedAt(Instant now);

  /** The key that signs with {@code algorithms} at {@code now}, with it's private part. */
  Optional<ActiveSigningKey> findSignerAt(SigningKeyAlgorithm algorithm, Instant now);

  /** Erases the private parts of keys that no longer sign. Returns how many were erased. */
  int erasePrivateKeysAt(Instant now);

  /** Delegates keys past their publication. Returns how many were deleted. */
  int deleteExpiredAt(Instant now);
}
