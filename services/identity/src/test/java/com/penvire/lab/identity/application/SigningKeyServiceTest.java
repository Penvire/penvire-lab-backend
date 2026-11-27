package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.audit.TestAuditScope;
import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The JWKS read and rotation: rotation only ever inserts keys. */
class SigningKeyServiceTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Duration SIGNING_PERIOD = Duration.ofDays(30);
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
  private static final Duration SKEW = Duration.ofSeconds(30);

  private final MutableClock clock = MutableClock.at(NOW);
  private final InMemorySigningKeys signingKeys = new InMemorySigningKeys(clock);
  private final FixedKeyPairs generator = new FixedKeyPairs();

  @Test
  void asksForTheKeysPublishedAtTheCurrentTime() {
    var service = service(Set.of(SigningKeyAlgorithm.ML_DSA_65));

    service.getJwksSet();
    clock.advance(Duration.ofDays(1));
    service.getJwksSet();

    assertEquals(List.of(NOW, NOW.plus(Duration.ofDays(1))), signingKeys.askedAt);
  }

  @Test
  void theFirstKeySignsImmediately() {
    service(Set.of(SigningKeyAlgorithm.ML_DSA_65)).rotate();

    var key = only(signingKeys.keys());
    assertEquals(SigningKeyAlgorithm.ML_DSA_65, key.algorithm());
    assertEquals(
        new SigningKeyLifetime(
            NOW,
            NOW,
            NOW.plus(SIGNING_PERIOD),
            NOW.plus(SIGNING_PERIOD).plus(TOKEN_LIFETIME).plus(SKEW)),
        key.lifetime());
  }

  @Test
  void storesTheGeneratedKeyPair() {
    service(Set.of(SigningKeyAlgorithm.ML_DSA_65)).rotate();

    var key = only(signingKeys.keys());
    var pair = generator.pair(SigningKeyAlgorithm.ML_DSA_65);
    assertSame(pair.getPublic(), key.publicKey());
    assertSame(pair.getPrivate(), signingKeys.privateKeys.get(key.id()));
  }

  @Test
  void createsAKeyForEveryAlgorithmInUse() {
    service(Set.of(SigningKeyAlgorithm.ML_DSA_65, SigningKeyAlgorithm.RS256)).rotate();

    assertEquals(
        Set.of(SigningKeyAlgorithm.ML_DSA_65, SigningKeyAlgorithm.RS256),
        Set.copyOf(signingKeys.keys().stream().map(SigningKey::algorithm).toList()));
    assertEquals(2, signingKeys.keys().size());
  }

  @Test
  void createsNoKeyForAnAlgorithmNotInUse() {
    service(Set.of(SigningKeyAlgorithm.ML_DSA_65)).rotate();

    assertTrue(
        signingKeys.keys().stream().noneMatch(key -> key.algorithm() == SigningKeyAlgorithm.RS256));
  }

  @Test
  void createsNothingWhileTheSignerHasLongToGo() {
    var service = service(Set.of(SigningKeyAlgorithm.ML_DSA_65));
    service.rotate();

    clock.advance(Duration.ofDays(10));
    service.rotate();

    assertEquals(1, signingKeys.keys().size());
  }

  @Test
  void theNextKeyIsPublishedNowAndSignsWhenTheCurrentOneStops() {
    var algorithm = SigningKeyAlgorithm.ML_DSA_65;
    var service = service(Set.of(algorithm, SigningKeyAlgorithm.RS256));
    service.rotate();
    var current =
        only(signingKeys.keys().stream().filter(key -> key.algorithm() == algorithm).toList());

    clock.set(current.lifetime().signUntil().minus(Duration.ofDays(1)));
    service.rotate();

    var next =
        only(
            signingKeys.keys().stream()
                .filter(key -> key.algorithm() == algorithm && !key.id().equals(current.id()))
                .toList());
    var signFrom = current.lifetime().signUntil();
    var signUntil = signFrom.plus(SIGNING_PERIOD);
    assertEquals(
        new SigningKeyLifetime(
            clock.instant(), signFrom, signUntil, signUntil.plus(TOKEN_LIFETIME).plus(SKEW)),
        next.lifetime());
  }

  @Test
  void theNextKeyIsPublishedBeforeItSigns() {
    var algorithm = SigningKeyAlgorithm.ML_DSA_65;
    var service = service(Set.of(algorithm, SigningKeyAlgorithm.RS256));
    service.rotate();
    var current =
        only(signingKeys.keys().stream().filter(key -> key.algorithm() == algorithm).toList());

    clock.set(current.lifetime().signUntil().minus(Duration.ofDays(1)));
    service.rotate();

    var next =
        only(
            signingKeys.keys().stream()
                .filter(key -> key.algorithm() == algorithm && !key.id().equals(current.id()))
                .toList());
    var jwksCacheLifetime = Duration.ofHours(1);
    assertFalse(
        next.lifetime().publishFrom().plus(jwksCacheLifetime).isAfter(next.lifetime().signFrom()),
        "consumers must be able to fetch the next key before it signs");
  }

  @Test
  void createsNoKeyWhileOneIsPending() {
    var service = service(Set.of(SigningKeyAlgorithm.ML_DSA_65));
    service.rotate();
    var current = only(signingKeys.keys());

    clock.set(current.lifetime().signUntil().minus(Duration.ofDays(1)));
    service.rotate();
    clock.advance(Duration.ofHours(1));
    service.rotate();

    assertEquals(2, signingKeys.keys().size());
  }

  @Test
  void aDailyRotationKeepsExactlyOneSignerPerAlgorithm() {
    var algorithms = Set.of(SigningKeyAlgorithm.ML_DSA_65, SigningKeyAlgorithm.RS256);
    var service = service(algorithms);

    for (int day = 0; day < 200; day++) {
      service.rotate();
      for (int hour = 0; hour < 24; hour++) {
        var now = clock.instant();
        for (var algorithm : algorithms) {
          var signers =
              signingKeys.keys().stream()
                  .filter(key -> key.algorithm() == algorithm && key.signsAt(now))
                  .count();
          assertEquals(1, signers, () -> algorithm + " signers at " + now);
        }
        clock.advance(Duration.ofHours(1));
      }
    }
  }

  @Test
  void rotationCleansUpEvenWhenNoNewKeyIsDue() {
    var service = service(Set.of(SigningKeyAlgorithm.ML_DSA_65, SigningKeyAlgorithm.RS256));
    service.rotate();

    clock.advance(Duration.ofDays(2));
    var now = clock.instant();
    signingKeys.calls.clear();
    signingKeys.deletedAt.clear();
    signingKeys.erasedAt.clear();

    service.rotate();

    assertEquals(List.of(now), signingKeys.deletedAt);
    assertEquals(List.of(now), signingKeys.erasedAt);
    assertFalse(signingKeys.writes().contains("insert"));
  }

  @Test
  void createsASignerAtOnceWhenNoKeySigns() {
    var service = service(Set.of(SigningKeyAlgorithm.ML_DSA_65));
    service.rotate();
    var revoked = only(signingKeys.keys());

    clock.advance(Duration.ofDays(3));
    signingKeys.revoke(revoked.id(), clock.instant());
    service.rotate();

    var now = clock.instant();
    var signer = only(signingKeys.keys().stream().filter(key -> key.signsAt(now)).toList());
    assertEquals(now, signer.lifetime().signFrom());
  }

  @Test
  void takesTheRotationLockBeforeAnythingElse() {
    service(Set.of(SigningKeyAlgorithm.ML_DSA_65)).rotate();

    assertEquals("lock", signingKeys.calls.getFirst());
  }

  private SigningKeyService service(Set<SigningKeyAlgorithm> algorithms) {
    var lifecycle =
        new SigningKeyLifecycle(
            signingKeys,
            generator,
            UUID::randomUUID,
            new TestJwtConfig(TOKEN_LIFETIME),
            new TestAuditScope());
    return new SigningKeyService(signingKeys, clock, lifecycle, new TestJwksConfig(algorithms));
  }

  private static SigningKey only(List<SigningKey> keys) {
    assertEquals(1, keys.size(), () -> "expected one key, got " + keys);
    return keys.getFirst();
  }
}
