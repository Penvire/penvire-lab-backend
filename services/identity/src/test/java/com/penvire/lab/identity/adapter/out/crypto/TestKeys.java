package com.penvire.lab.identity.adapter.out.crypto;

import com.penvire.lab.identity.config.SigningKeyConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** Fixed KEKs and generated key pairs for the crypto adapter tests; never real secrets. */
final class TestKeys {

  static final String K1 = Base64.getEncoder().encodeToString(filled(1));
  static final String K2 = Base64.getEncoder().encodeToString(filled(2));

  static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
  static final SigningKeyLifetime LIFETIME =
      new SigningKeyLifetime(T0, T0.plusSeconds(60), T0.plusSeconds(3600), T0.plusSeconds(3660));

  private TestKeys() {}

  record Config(String current, Map<String, String> keys) implements SigningKeyConfig {}

  static KeyEncryptionRing ring(String current, Map<String, String> keys) {
    return new KeyEncryptionRing(new Config(current, keys));
  }

  static KeyPair pair(SigningKeyAlgorithm algorithm) {
    try {
      return KeyPairGenerator.getInstance(generatorName(algorithm)).generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static SigningKey key(SigningKeyAlgorithm algorithm, KeyPair pair) {
    return new SigningKey(UUID.randomUUID(), algorithm, pair.getPublic(), LIFETIME);
  }

  private static String generatorName(SigningKeyAlgorithm algorithm) {
    return switch (algorithm) {
      case ML_DSA_65 -> "ML-DSA-65";
      case RS256 -> "RSA";
    };
  }

  private static byte[] filled(int value) {
    byte[] bytes = new byte[32];
    Arrays.fill(bytes, (byte) value);
    return bytes;
  }
}
