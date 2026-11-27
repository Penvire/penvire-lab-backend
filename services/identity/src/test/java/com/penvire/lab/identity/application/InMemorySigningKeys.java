package com.penvire.lab.identity.application;

import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyPersistencePort;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Keeps signing keys in memory and records every port call, in order. */
final class InMemorySigningKeys implements SigningKeyPersistencePort {
  private final MutableClock clock;
  private final Map<UUID, SigningKey> keys = new LinkedHashMap<>();
  final Map<UUID, PrivateKey> privateKeys = new LinkedHashMap<>();
  final List<String> calls = new ArrayList<>();
  final List<Instant> askedAt = new ArrayList<>();
  final List<Instant> erasedAt = new ArrayList<>();
  final List<Instant> deletedAt = new ArrayList<>();

  InMemorySigningKeys(MutableClock clock) {
    this.clock = clock;
  }

  List<SigningKey> keys() {
    return List.copyOf(keys.values());
  }

  SigningKey get(UUID id) {
    return Objects.requireNonNull(keys.get(id));
  }

  /** Stores a key without recording a call, to set up a test. */
  void given(SigningKey key, PrivateKey privateKey) {
    keys.put(key.id(), key);
    privateKeys.put(key.id(), privateKey);
  }

  /** The port calls that change stored keys. */
  List<String> writes() {
    return calls.stream().filter(call -> !call.equals("lock") && !call.startsWith("find")).toList();
  }

  @Override
  public void lockRotation() {
    calls.add("lock");
  }

  @Override
  public void insert(SigningKey key, PrivateKey privateKey) {
    calls.add("insert");
    keys.put(key.id(), key);
    privateKeys.put(key.id(), privateKey);
  }

  @Override
  public void retire(UUID id, Duration retention) {
    calls.add("retire");
    keys.computeIfPresent(id, (ignored, key) -> key.retireAt(clock.instant(), retention));
  }

  @Override
  public void revoke(UUID id, Instant revokedAt) {
    calls.add("revoke");
    keys.computeIfPresent(id, (ignored, key) -> key.revokeAt(revokedAt));
    privateKeys.remove(id);
  }

  @Override
  public List<SigningKey> findPublishedAt(Instant now) {
    calls.add("findPublished");
    askedAt.add(now);
    return keys.values().stream().filter(key -> key.isPublishedAt(now)).toList();
  }

  @Override
  public Optional<ActiveSigningKey> findSignerAt(SigningKeyAlgorithm algorithm, Instant now) {
    calls.add("findSigner");
    return keys.values().stream()
        .filter(key -> key.algorithm() == algorithm && key.signsAt(now))
        .findFirst()
        .map(key -> new ActiveSigningKey(key, Objects.requireNonNull(privateKeys.get(key.id()))));
  }

  @Override
  public int erasePrivateKeysAt(Instant now) {
    calls.add("erase");
    erasedAt.add(now);
    return 0;
  }

  @Override
  public int deleteExpiredAt(Instant now) {
    calls.add("delete");
    deletedAt.add(now);
    return 0;
  }
}
