package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.out.SigningKeyPersistencePort;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Signing keys for the token tests: one key that signs, from a port that only finds it. */
final class TestSigningKeys {

  private TestSigningKeys() {}

  static KeyPair pair(SigningKeyAlgorithm algorithm) {
    try {
      return KeyPairGenerator.getInstance(generatorName(algorithm)).generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A key that signs from {@code from} for 30 days. */
  static ActiveSigningKey active(SigningKeyAlgorithm algorithm, KeyPair pair, Instant from) {
    var signUntil = from.plus(Duration.ofDays(30));
    var lifetime =
        new SigningKeyLifetime(from, from, signUntil, signUntil.plus(Duration.ofHours(1)));
    var key = new SigningKey(UUID.randomUUID(), algorithm, pair.getPublic(), lifetime);
    return new ActiveSigningKey(key, pair.getPrivate());
  }

  /** A port whose only stored key is {@code signer}. */
  static SigningKeyPersistencePort onlySigner(ActiveSigningKey signer) {
    return new OnlySigner(signer);
  }

  private static String generatorName(SigningKeyAlgorithm algorithm) {
    return switch (algorithm) {
      case ML_DSA_65 -> "ML-DSA-65";
      case RS256 -> "RSA";
    };
  }

  /** Finds {@code signer} while it signs; every other operation is not expected. */
  private record OnlySigner(ActiveSigningKey signer) implements SigningKeyPersistencePort {

    @Override
    public Optional<ActiveSigningKey> findSignerAt(SigningKeyAlgorithm algorithm, Instant now) {
      var key = signer.key();
      return key.algorithm() == algorithm && key.signsAt(now)
          ? Optional.of(signer)
          : Optional.empty();
    }

    @Override
    public void insert(SigningKey key, PrivateKey privateKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void retire(UUID id, Duration retention) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void revoke(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<SigningKey> findPublishedAt(Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int erasePrivateKeysAt(Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int deleteExpiredAt(Instant now) {
      throw new UnsupportedOperationException();
    }
  }
}
