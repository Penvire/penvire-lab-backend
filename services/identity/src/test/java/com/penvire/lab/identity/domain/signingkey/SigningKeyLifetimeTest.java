package com.penvire.lab.identity.domain.signingkey;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SigningKeyLifetimeTest {

  private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
  private static final Duration RETENTION = Duration.ofMinutes(5);

  private static Instant at(long hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  // Published at T0, signs from hour 1 to hour 721 (30 days), removed one hour later.
  private static final SigningKeyLifetime LIFETIME =
      new SigningKeyLifetime(at(0), at(1), at(721), at(722));

  private static SigningKeyLifetime revokedAt(Instant revokedAt) {
    return new SigningKeyLifetime(at(0), at(1), at(721), at(722), revokedAt);
  }

  // Construction

  @ParameterizedTest(name = "[{index}] {0} {1} {2} {3}")
  @CsvSource({
    // signFrom before publishFrom: a key would sign before verifiers can know it.
    "1, 0, 721, 722",
    // signUntil before signFrom.
    "0, 721, 1, 722",
    // publishUntil before signUntil: tokens would outlive their key.
    "0, 1, 722, 721",
    // A window that signs for no time at all.
    "0, 1, 1, 722",
  })
  void rejectsInstantsOutOfOrder(
      long publishFrom, long signFrom, long signUntil, long publishUntil) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SigningKeyLifetime(at(publishFrom), at(signFrom), at(signUntil), at(publishUntil)));
  }

  @Test
  void allowsTheFirstKeyToSignAsSoonAsItIsPublished() {
    assertDoesNotThrow(() -> new SigningKeyLifetime(at(0), at(0), at(720), at(721)));
  }

  @Test
  void allowsAKeyToBeRemovedAsSoonAsItStopsSigning() {
    assertDoesNotThrow(() -> new SigningKeyLifetime(at(0), at(1), at(721), at(721)));
  }

  @Test
  void isNotRevokedWhenCreatedWithoutARevocation() {
    assertNull(LIFETIME.revokedAt());
  }

  @Test
  void rejectsARevocationBeforeTheKeyWasPublished() {
    assertThrows(IllegalArgumentException.class, () -> revokedAt(at(0).minusNanos(1)));
  }

  @Test
  void rejectsARevocationAfterTheKeyExpired() {
    assertThrows(IllegalArgumentException.class, () -> revokedAt(at(722)));
  }

  // Windows: start inclusive, end exclusive

  @Test
  void isPublishedFromPublishFromInclusiveToPublishUntilExclusive() {
    assertFalse(LIFETIME.isPublishedAt(at(0).minusNanos(1)));
    assertTrue(LIFETIME.isPublishedAt(at(0)));
    assertTrue(LIFETIME.isPublishedAt(at(722).minusNanos(1)));
    assertFalse(LIFETIME.isPublishedAt(at(722)));
  }

  @Test
  void signsFromSignFromInclusiveToSignUntilExclusive() {
    assertFalse(LIFETIME.signsAt(at(1).minusNanos(1)));
    assertTrue(LIFETIME.signsAt(at(1)));
    assertTrue(LIFETIME.signsAt(at(721).minusNanos(1)));
    assertFalse(LIFETIME.signsAt(at(721)));
  }

  @Test
  void holdsThePrivateKeyUntilSignUntilExclusive() {
    assertTrue(LIFETIME.holdsPrivateKeyAt(at(0)));
    assertTrue(LIFETIME.holdsPrivateKeyAt(at(721).minusNanos(1)));
    assertFalse(LIFETIME.holdsPrivateKeyAt(at(721)));
  }

  // Revocation ends publishing, signing and the private key at once

  @Test
  void isRevokedFromItsRevocationOn() {
    var revoked = revokedAt(at(10));

    assertFalse(revoked.isRevokedAt(at(10).minusNanos(1)));
    assertTrue(revoked.isRevokedAt(at(10)));
    assertFalse(LIFETIME.isRevokedAt(at(10)));
  }

  @Test
  void isNeitherPublishedNorSigningNorHoldingThePrivateKeyOnceRevoked() {
    var revoked = revokedAt(at(10));

    assertAll(
        () -> assertTrue(revoked.isPublishedAt(at(10).minusNanos(1))),
        () -> assertTrue(revoked.signsAt(at(10).minusNanos(1))),
        () -> assertFalse(revoked.isPublishedAt(at(10))),
        () -> assertFalse(revoked.signsAt(at(10))),
        () -> assertFalse(revoked.holdsPrivateKeyAt(at(10))));
  }

  // Phases, derived from the instants and the revocation

  @Test
  void derivesItsPhaseFromTheInstants() {
    assertAll(
        () -> assertEquals(SigningKeyPhase.PENDING, LIFETIME.phaseAt(at(0))),
        () -> assertEquals(SigningKeyPhase.SIGNING, LIFETIME.phaseAt(at(1))),
        () -> assertEquals(SigningKeyPhase.SIGNING, LIFETIME.phaseAt(at(721).minusNanos(1))),
        () -> assertEquals(SigningKeyPhase.RETIRED, LIFETIME.phaseAt(at(721))),
        () -> assertEquals(SigningKeyPhase.RETIRED, LIFETIME.phaseAt(at(722).minusNanos(1))),
        () -> assertEquals(SigningKeyPhase.EXPIRED, LIFETIME.phaseAt(at(722))));
  }

  @Test
  void isRevokedFromItsRevocationUntilItWouldHaveExpired() {
    var revoked = revokedAt(at(10));

    assertAll(
        () -> assertEquals(SigningKeyPhase.SIGNING, revoked.phaseAt(at(10).minusNanos(1))),
        () -> assertEquals(SigningKeyPhase.REVOKED, revoked.phaseAt(at(10))),
        () -> assertEquals(SigningKeyPhase.REVOKED, revoked.phaseAt(at(722).minusNanos(1))),
        () -> assertEquals(SigningKeyPhase.EXPIRED, revoked.phaseAt(at(722))));
  }

  // Retiring: stops signing, tokens stay valid

  @Test
  void retiringStopsSigningNowAndStaysPublishedForTheRetention() {
    assertEquals(
        new SigningKeyLifetime(at(0), at(1), at(100), at(100).plus(RETENTION)),
        LIFETIME.retireAt(at(100), RETENTION));
  }

  @Test
  void cannotRetireALifetimeThatDoesNotSignYet() {
    assertThrows(IllegalStateException.class, () -> LIFETIME.retireAt(at(0), RETENTION));
  }

  @Test
  void cannotRetireALifetimeThatNoLongerSigns() {
    assertThrows(IllegalStateException.class, () -> LIFETIME.retireAt(at(721), RETENTION));
  }

  @Test
  void cannotRetireARevokedLifetime() {
    assertThrows(IllegalStateException.class, () -> revokedAt(at(10)).retireAt(at(100), RETENTION));
  }

  // Revoking: tokens become invalid at once, the instants stay

  @Test
  void revokingKeepsTheInstantsAndRecordsTheRevocation() {
    assertEquals(revokedAt(at(100)), LIFETIME.revokeAt(at(100)));
  }

  @Test
  void canRevokeWhilePendingSigningOrRetired() {
    assertAll(
        () -> assertEquals(at(0), LIFETIME.revokeAt(at(0)).revokedAt()),
        () -> assertEquals(at(100), LIFETIME.revokeAt(at(100)).revokedAt()),
        () -> assertEquals(at(721), LIFETIME.revokeAt(at(721)).revokedAt()));
  }

  @Test
  void cannotRevokeTwice() {
    assertThrows(IllegalStateException.class, () -> revokedAt(at(10)).revokeAt(at(100)));
  }

  @Test
  void cannotRevokeAnExpiredLifetime() {
    assertThrows(IllegalStateException.class, () -> LIFETIME.revokeAt(at(722)));
  }
}
