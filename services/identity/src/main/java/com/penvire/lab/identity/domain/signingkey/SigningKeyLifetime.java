package com.penvire.lab.identity.domain.signingkey;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * When a signing key is published and when it signs (ADR 0007), and whether it was revoked (ADR
 * 0009). Every window starts inclusive and ends exclusive.
 */
public record SigningKeyLifetime(
    Instant publishFrom,
    Instant signFrom,
    Instant signUntil,
    Instant publishUntil,
    @Nullable Instant revokedAt) {

  public SigningKeyLifetime {
    if (signFrom.isBefore(publishFrom)) {
      throw new IllegalArgumentException("Signing key cannot sign before it is published");
    }

    if (!signUntil.isAfter(signFrom)) {
      throw new IllegalArgumentException("Signing key must sign for some time");
    }

    if (publishUntil.isBefore(signUntil)) {
      throw new IllegalArgumentException(
          "Signing key must stay published at least until it stops signing");
    }

    if (revokedAt != null && !within(revokedAt, publishFrom, publishUntil)) {
      throw new IllegalArgumentException("Signing key can only be revoked while it is published.");
    }
  }

  /** A lifetime that has never been revoked */
  public SigningKeyLifetime(
      Instant publishFrom, Instant signFrom, Instant signUntil, Instant publishUntil) {
    this(publishFrom, signFrom, signUntil, publishUntil, null);
  }

  /** Whether the key was revoked at or before {@code now}. */
  public boolean isRevokedAt(Instant now) {
    return revokedAt != null && !now.isBefore(revokedAt);
  }

  /** Whether the key's public part may appear in the JWKS at {@code now}. */
  public boolean isPublishedAt(Instant now) {
    return within(now, publishFrom, publishUntil) && !isRevokedAt(now);
  }

  /** Whether the key signs new tokens at {@code now}. */
  public boolean signsAt(Instant now) {
    return within(now, signFrom, signUntil) && !isRevokedAt(now);
  }

  /** Whether the key's private part may still exist at {@code now}. */
  public boolean holdsPrivateKeyAt(Instant now) {
    return now.isBefore(signUntil) && !isRevokedAt(now);
  }

  public SigningKeyPhase phaseAt(Instant now) {
    if (!now.isBefore(publishUntil)) {
      return SigningKeyPhase.EXPIRED;
    }
    if (isRevokedAt(now)) {
      return SigningKeyPhase.REVOKED;
    }
    if (now.isBefore(signFrom)) {
      return SigningKeyPhase.PENDING;
    }
    if (now.isBefore(signUntil)) {
      return SigningKeyPhase.SIGNING;
    }

    return SigningKeyPhase.RETIRED;
  }

  private static boolean within(Instant now, Instant from, Instant until) {
    return !now.isBefore(from) && now.isBefore(until);
  }

  /**
   * Stops signing at {@code now}; tokens signed so far stay valid, so the key stays published for
   * {@code retention}.
   *
   * @throws IllegalStateException if the key does not sign at {@code now}
   */
  public SigningKeyLifetime retireAt(Instant now, Duration retention) {
    if (!signsAt(now)) {
      throw new IllegalStateException("Only a signing key that currently signs can be retired");
    }
    return new SigningKeyLifetime(publishFrom, signFrom, now, now.plus(retention));
  }

  /**
   * Revokes at {@code now}: from then on the key is neither published nor signing, and tokens it
   * signed no longer verify. The instants stay, so the key shows as revoked until it would have
   * expired.
   *
   * @throws IllegalStateException if the key is already revoked or has expired
   */
  public SigningKeyLifetime revokeAt(Instant now) {
    if (revokedAt != null) {
      throw new IllegalStateException("The signing key is already revoked");
    }
    if (!now.isBefore(publishUntil)) {
      throw new IllegalStateException("An expired signing key cannot be revoked");
    }
    return new SigningKeyLifetime(publishFrom, signFrom, signUntil, publishUntil, now);
  }
}
