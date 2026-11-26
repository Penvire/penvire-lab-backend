package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.audit.TestAuditScope;
import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Revoking a compromised signer (ADR 0009). Rotation fills the gap before a pending successor. */
class SigningKeyRevocationTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

  private final MutableClock clock = MutableClock.at(NOW);
  private final InMemorySigningKeys signingKeys = new InMemorySigningKeys(clock);
  private final FixedKeyPairs generator = new FixedKeyPairs();
  private final SigningKeyLifecycle lifecycle =
      new SigningKeyLifecycle(
          signingKeys,
          generator,
          UUID::randomUUID,
          new TestJwtConfig(TOKEN_LIFETIME),
          new TestAuditScope());
  private final SigningKeyService service =
      new SigningKeyService(
          signingKeys,
          clock,
          lifecycle,
          new TestJwksConfig(Set.of(SigningKeyAlgorithm.ML_DSA_65, SigningKeyAlgorithm.RS256)));

  @Test
  void revokesTheKey() {
    var signer = given(SigningKeyAlgorithm.ML_DSA_65, signing());

    service.revoke(signer.algorithm());

    assertTrue(signingKeys.get(signer.id()).lifetime().isRevoked());
  }

  @Test
  void revokingTheSignerKeepsThePendingSuccessor() {
    var signer = given(SigningKeyAlgorithm.ML_DSA_65, signing());
    var pending = given(SigningKeyAlgorithm.ML_DSA_65, pending());

    service.revoke(signer.algorithm());

    assertFalse(signingKeys.get(pending.id()).lifetime().isRevoked());
    var handoff = pending.lifetime().signFrom();
    var replacement = signingKeys.findSignerAt(signer.algorithm(), NOW).orElseThrow();
    assertEquals(handoff, replacement.key().lifetime().signUntil());
    assertEquals(
        pending.id(),
        signingKeys.findSignerAt(signer.algorithm(), handoff).orElseThrow().key().id());
  }

  @Test
  void revokingTheSignerLeavesOtherAlgorithmsAlone() {
    var signer = given(SigningKeyAlgorithm.ML_DSA_65, signing());
    var rsaSigner = given(SigningKeyAlgorithm.RS256, signing());
    var rsaPending = given(SigningKeyAlgorithm.RS256, pending());

    service.revoke(signer.algorithm());

    assertFalse(signingKeys.get(rsaSigner.id()).lifetime().isRevoked());
    assertFalse(signingKeys.get(rsaPending.id()).lifetime().isRevoked());
  }

  @Test
  void afterRevokingTheSignerRotationKeepsExactlyOneSigner() {
    var signer = given(SigningKeyAlgorithm.ML_DSA_65, signing());
    given(SigningKeyAlgorithm.ML_DSA_65, pending());

    service.revoke(signer.algorithm());
    service.rotate();

    for (int hour = 0; hour < 24 * 40; hour++) {
      if (hour % 24 == 0) {
        service.rotate();
      }
      var now = clock.instant();
      var signers =
          signingKeys.keys().stream()
              .filter(key -> key.algorithm() == SigningKeyAlgorithm.ML_DSA_65)
              .filter(key -> key.signsAt(now))
              .count();
      assertEquals(1, signers, () -> "ML-DSA-65 signers at " + now);
      clock.advance(Duration.ofHours(1));
    }
  }

  @Test
  void takesTheRotationLockBeforeRevoking() {
    var signer = given(SigningKeyAlgorithm.ML_DSA_65, signing());

    service.revoke(signer.algorithm());

    assertEquals("lock", signingKeys.calls.getFirst());
  }

  private SigningKey given(SigningKeyAlgorithm algorithm, SigningKeyLifetime lifetime) {
    var pair = generator.pair(algorithm);
    var key = new SigningKey(UUID.randomUUID(), algorithm, pair.getPublic(), lifetime);
    signingKeys.given(key, pair.getPrivate());
    return key;
  }

  /** Signs now, for another day. */
  private static SigningKeyLifetime signing() {
    var signUntil = NOW.plus(Duration.ofDays(1));
    return new SigningKeyLifetime(
        NOW.minus(Duration.ofDays(29)),
        NOW.minus(Duration.ofDays(29)),
        signUntil,
        signUntil.plus(TOKEN_LIFETIME));
  }

  /** Published an hour ago, signs once the signer stops. */
  private static SigningKeyLifetime pending() {
    var signFrom = NOW.plus(Duration.ofDays(1));
    var signUntil = signFrom.plus(Duration.ofDays(30));
    return new SigningKeyLifetime(
        NOW.minus(Duration.ofHours(1)), signFrom, signUntil, signUntil.plus(TOKEN_LIFETIME));
  }
}
