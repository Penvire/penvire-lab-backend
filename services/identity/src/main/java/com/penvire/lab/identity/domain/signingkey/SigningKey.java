package com.penvire.lab.identity.domain.signingkey;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.NamedParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A key Identity signs tokens with (ADR 0007). Holds only the public part; the private part stays
 * in the persistence adapter and is handed out only to sign. Compare keys by {@link #id()}: two
 * versions of the same key, before and after retiring, are not {@code equals}.
 */
public record SigningKey(
    UUID id, SigningKeyAlgorithm algorithm, PublicKey publicKey, SigningKeyLifetime lifetime) {

  private static final int MIN_RSA_MODULUS_BITS = 2048;

  public SigningKey {
    if (!fits(algorithm, publicKey)) {
      throw new IllegalArgumentException("The key does not fit the algorithm");
    }
  }

  /** The JWS {@code kid} */
  public String kid() {
    return id.toString();
  }

  public boolean isPublishedAt(Instant now) {
    return lifetime.isPublishedAt(now);
  }

  public boolean signsAt(Instant now) {
    return lifetime.signsAt(now);
  }

  public boolean holdsPrivateKeyAt(Instant now) {
    return lifetime.holdsPrivateKeyAt(now);
  }

  public SigningKeyPhase phaseAt(Instant now) {
    return lifetime.phaseAt(now);
  }

  /** See {@link SigningKeyLifetime#retireAt}. */
  public SigningKey retireAt(Instant now, Duration retention) {
    return withLifetime(lifetime.retireAt(now, retention));
  }

  /** See {@link SigningKeyLifetime#revokeAt}. */
  public SigningKey revokeAt(Instant now) {
    return withLifetime(lifetime.revokeAt(now));
  }

  private SigningKey withLifetime(SigningKeyLifetime changed) {
    return new SigningKey(id, algorithm, publicKey, changed);
  }

  private static boolean fits(SigningKeyAlgorithm algorithm, PublicKey publicKey) {
    return switch (algorithm) {
      case ML_DSA_65 ->
          publicKey.getParams() instanceof NamedParameterSpec spec
              && NamedParameterSpec.ML_DSA_65.getName().equals(spec.getName());
      case RS256 ->
          publicKey instanceof RSAPublicKey rsa
              && rsa.getModulus().bitLength() >= MIN_RSA_MODULUS_BITS;
    };
  }
}
