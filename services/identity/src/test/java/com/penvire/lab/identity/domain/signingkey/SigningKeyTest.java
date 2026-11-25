package com.penvire.lab.identity.domain.signingkey;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The key's own rules; the time rules are in {@link SigningKeyLifetimeTest}. */
class SigningKeyTest {

  private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
  private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final Duration RETENTION = Duration.ofMinutes(5);

  private static final PublicKey ML_DSA_65 = mlDsa(NamedParameterSpec.ML_DSA_65);
  private static final PublicKey ML_DSA_44 = mlDsa(NamedParameterSpec.ML_DSA_44);
  private static final PublicKey RSA_2048 = rsa(2048);
  private static final PublicKey RSA_1024 = rsa(1024);

  private static Instant at(long hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  // Published at T0, signs from hour 1 to hour 721 (30 days), removed one hour later.
  private static final SigningKeyLifetime LIFETIME =
      new SigningKeyLifetime(at(0), at(1), at(721), at(722));

  private static SigningKey key(SigningKeyAlgorithm algorithm, PublicKey publicKey) {
    return new SigningKey(ID, algorithm, publicKey, LIFETIME);
  }

  private static SigningKey key() {
    return key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA_65);
  }

  @Test
  void usesItsIdAsKid() {
    assertEquals("019a0000-0000-7000-8000-000000000001", key().kid());
  }

  // Construction

  @Test
  void acceptsAKeyThatFitsItsAlgorithm() {
    assertAll(
        () -> assertDoesNotThrow(() -> key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA_65)),
        () -> assertDoesNotThrow(() -> key(SigningKeyAlgorithm.RS256, RSA_2048)));
  }

  @Test
  void rejectsAnRsaKeyForMlDsa65() {
    assertThrows(
        IllegalArgumentException.class, () -> key(SigningKeyAlgorithm.ML_DSA_65, RSA_2048));
  }

  @Test
  void rejectsAnotherMlDsaParameterSetForMlDsa65() {
    assertThrows(
        IllegalArgumentException.class, () -> key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA_44));
  }

  @Test
  void rejectsAnMlDsaKeyForRs256() {
    assertThrows(IllegalArgumentException.class, () -> key(SigningKeyAlgorithm.RS256, ML_DSA_65));
  }

  @Test
  void rejectsRsaKeysShorterThan2048BitsForRs256() {
    assertThrows(IllegalArgumentException.class, () -> key(SigningKeyAlgorithm.RS256, RSA_1024));
  }

  // Delegation to the lifetime

  @Test
  void answersTimeQuestionsFromItsLifetime() {
    var key = key();

    assertAll(
        () -> assertTrue(key.isPublishedAt(at(10))),
        () -> assertTrue(key.signsAt(at(10))),
        () -> assertTrue(key.holdsPrivateKeyAt(at(10))),
        () -> assertEquals(SigningKeyPhase.SIGNING, key.phaseAt(at(10))),
        () -> assertFalse(key.isPublishedAt(at(722))),
        () -> assertFalse(key.signsAt(at(721))),
        () -> assertFalse(key.holdsPrivateKeyAt(at(721))),
        () -> assertEquals(SigningKeyPhase.EXPIRED, key.phaseAt(at(722))));
  }

  @Test
  void retiringChangesOnlyTheLifetime() {
    var key = key();

    var retired = key.retireAt(at(100), RETENTION);

    assertAll(
        () -> assertEquals(LIFETIME.retireAt(at(100), RETENTION), retired.lifetime()),
        () -> assertEquals(ID, retired.id()),
        () -> assertEquals(SigningKeyAlgorithm.ML_DSA_65, retired.algorithm()),
        () -> assertSame(ML_DSA_65, retired.publicKey()),
        () -> assertEquals(LIFETIME, key.lifetime()));
  }

  @Test
  void revokingChangesOnlyTheLifetime() {
    var key = key();

    var revoked = key.revokeAt(at(100));

    assertAll(
        () -> assertEquals(LIFETIME.revokeAt(at(100)), revoked.lifetime()),
        () -> assertEquals(ID, revoked.id()),
        () -> assertEquals(SigningKeyAlgorithm.ML_DSA_65, revoked.algorithm()),
        () -> assertSame(ML_DSA_65, revoked.publicKey()),
        () -> assertEquals(SigningKeyPhase.REVOKED, revoked.phaseAt(at(100))),
        () -> assertEquals(LIFETIME, key.lifetime()));
  }

  private static PublicKey mlDsa(AlgorithmParameterSpec parameterSet) {
    try {
      var generator = KeyPairGenerator.getInstance("ML-DSA");
      generator.initialize(parameterSet);
      return generator.generateKeyPair().getPublic();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static PublicKey rsa(int bits) {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(bits);
      return generator.generateKeyPair().getPublic();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
