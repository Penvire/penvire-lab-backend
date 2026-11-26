package com.penvire.lab.identity.adapter.out.crypto.signingkey;

import static com.penvire.lab.identity.adapter.out.crypto.signingkey.TestKeys.K1;
import static com.penvire.lab.identity.adapter.out.crypto.signingkey.TestKeys.K2;
import static com.penvire.lab.identity.adapter.out.crypto.signingkey.TestKeys.T0;
import static com.penvire.lab.identity.adapter.out.crypto.signingkey.TestKeys.key;
import static com.penvire.lab.identity.adapter.out.crypto.signingkey.TestKeys.pair;
import static com.penvire.lab.identity.adapter.out.crypto.signingkey.TestKeys.ring;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.application.port.out.signingkey.SealedPrivateKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AesGcmSigningKeyProtectorAdapterTest {

  private static final KeyPair ML_DSA = pair(SigningKeyAlgorithm.ML_DSA_65);
  private static final KeyPair RSA = pair(SigningKeyAlgorithm.RS256);

  private final AesGcmSigningKeyProtectorAdapter protector =
      new AesGcmSigningKeyProtectorAdapter(ring("k1", Map.of("k1", K1, "k2", K2)));

  private static KeyPair pairFor(SigningKeyAlgorithm algorithm) {
    return algorithm == SigningKeyAlgorithm.ML_DSA_65 ? ML_DSA : RSA;
  }

  private static byte[] flipFirstBit(byte[] bytes) {
    byte[] copy = bytes.clone();
    copy[0] ^= 1;
    return copy;
  }

  // Sealing

  @ParameterizedTest
  @EnumSource(SigningKeyAlgorithm.class)
  void unsealsWhatItSealed(SigningKeyAlgorithm algorithm) {
    var pair = pairFor(algorithm);
    var key = key(algorithm, pair);

    var sealed = protector.seal(key, pair.getPrivate());

    assertArrayEquals(pair.getPrivate().getEncoded(), protector.unseal(key, sealed).getEncoded());
  }

  @ParameterizedTest
  @EnumSource(SigningKeyAlgorithm.class)
  void onlySealsThePrivateKeyCorrespondingToThePublicKey(SigningKeyAlgorithm algorithm) {
    var key = key(algorithm, pairFor(algorithm));
    var other = pair(algorithm);

    assertThrows(IllegalArgumentException.class, () -> protector.seal(key, other.getPrivate()));
  }

  @Test
  void rejectsAPrivateKeyOfAnotherAlgorithm() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);

    assertThrows(IllegalArgumentException.class, () -> protector.seal(key, RSA.getPrivate()));
  }

  @Test
  void sealsUnderTheCurrentKekWithA12ByteNonce() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);

    var sealed = protector.seal(key, ML_DSA.getPrivate());

    assertAll(
        () -> assertEquals("k1", sealed.kekId()),
        () -> assertEquals(12, sealed.nonce().length),
        // PKCS#8 plus the 16-byte GCM tag.
        () ->
            assertEquals(ML_DSA.getPrivate().getEncoded().length + 16, sealed.ciphertext().length));
  }

  @Test
  void usesAFreshNonceForEverySeal() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);

    var first = protector.seal(key, ML_DSA.getPrivate());
    var second = protector.seal(key, ML_DSA.getPrivate());

    assertFalse(Arrays.equals(first.nonce(), second.nonce()));
    assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
  }

  @Test
  void neverPrintsTheSealedBytes() {
    var sealed = protector.seal(key(SigningKeyAlgorithm.RS256, RSA), RSA.getPrivate());

    assertEquals("SealedPrivateKey[k1]", sealed.toString());
  }

  // Unsealing: every change to the ciphertext or what it is bound to fails

  @Test
  void rejectsATamperedCiphertext() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var sealed = protector.seal(key, ML_DSA.getPrivate());
    var tampered =
        new SealedPrivateKey(sealed.kekId(), sealed.nonce(), flipFirstBit(sealed.ciphertext()));

    assertThrows(IllegalStateException.class, () -> protector.unseal(key, tampered));
  }

  @Test
  void rejectsATamperedNonce() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var sealed = protector.seal(key, ML_DSA.getPrivate());
    var tampered =
        new SealedPrivateKey(sealed.kekId(), flipFirstBit(sealed.nonce()), sealed.ciphertext());

    assertThrows(IllegalStateException.class, () -> protector.unseal(key, tampered));
  }

  @Test
  void rejectsACiphertextMovedToAnotherKeyId() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var sealed = protector.seal(key, ML_DSA.getPrivate());
    var otherId =
        new SigningKey(UUID.randomUUID(), key.algorithm(), key.publicKey(), key.lifetime());

    assertThrows(IllegalStateException.class, () -> protector.unseal(otherId, sealed));
  }

  @Test
  void rejectsACiphertextMovedToAnotherPublicKey() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var sealed = protector.seal(key, ML_DSA.getPrivate());
    var otherPublicKey =
        new SigningKey(
            key.id(),
            key.algorithm(),
            pair(SigningKeyAlgorithm.ML_DSA_65).getPublic(),
            key.lifetime());

    assertThrows(IllegalStateException.class, () -> protector.unseal(otherPublicKey, sealed));
  }

  @Test
  void rejectsACiphertextRelabelledWithAnotherKnownKek() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var sealed = protector.seal(key, ML_DSA.getPrivate());
    var relabelled = new SealedPrivateKey("k2", sealed.nonce(), sealed.ciphertext());

    assertThrows(IllegalStateException.class, () -> protector.unseal(key, relabelled));
  }

  @Test
  void rejectsAnUnknownKek() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var sealed = protector.seal(key, ML_DSA.getPrivate());
    var unknown = new SealedPrivateKey("gone", sealed.nonce(), sealed.ciphertext());

    assertThrows(IllegalStateException.class, () -> protector.unseal(key, unknown));
  }

  // KEK rotation

  @Test
  void readsKeysSealedUnderAnOlderKekAfterRotation() {
    var key = key(SigningKeyAlgorithm.RS256, RSA);
    var before = new AesGcmSigningKeyProtectorAdapter(ring("k1", Map.of("k1", K1)));
    var sealed = before.seal(key, RSA.getPrivate());
    var mac = before.mac(key, "k1");

    var after = new AesGcmSigningKeyProtectorAdapter(ring("k2", Map.of("k1", K1, "k2", K2)));

    assertArrayEquals(RSA.getPrivate().getEncoded(), after.unseal(key, sealed).getEncoded());
    assertTrue(after.isIntact(key, "k1", mac));
    assertEquals("k2", after.seal(key, RSA.getPrivate()).kekId());
  }

  @Test
  void stopsTrustingRowsOnceTheOldKekIsRemoved() {
    var key = key(SigningKeyAlgorithm.RS256, RSA);
    var before = new AesGcmSigningKeyProtectorAdapter(ring("k1", Map.of("k1", K1)));
    var sealed = before.seal(key, RSA.getPrivate());
    var mac = before.mac(key, "k1");

    var after = new AesGcmSigningKeyProtectorAdapter(ring("k2", Map.of("k2", K2)));

    assertFalse(after.isIntact(key, "k1", mac));
    assertThrows(IllegalStateException.class, () -> after.unseal(key, sealed));
  }

  // Row MAC

  @Test
  void macIsAnHmacSha256ThatVerifies() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);

    var mac = protector.mac(key, "k1");

    assertEquals(32, mac.length);
    assertTrue(protector.isIntact(key, "k1", mac));
  }

  @Test
  void macDependsOnTheKek() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var mac = protector.mac(key, "k1");

    assertFalse(Arrays.equals(mac, protector.mac(key, "k2")));
    assertFalse(protector.isIntact(key, "k2", mac));
  }

  @Test
  void macFailsForAnUnknownKek() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var mac = protector.mac(key, "k1");

    assertFalse(protector.isIntact(key, "gone", mac));
    assertThrows(IllegalStateException.class, () -> protector.mac(key, "gone"));
  }

  @Test
  void macFailsForATamperedMac() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);

    assertFalse(protector.isIntact(key, "k1", flipFirstBit(protector.mac(key, "k1"))));
    assertFalse(protector.isIntact(key, "k1", new byte[0]));
  }

  @Test
  void macCoversTheId() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var mac = protector.mac(key, "k1");
    var changed =
        new SigningKey(UUID.randomUUID(), key.algorithm(), key.publicKey(), key.lifetime());

    assertFalse(protector.isIntact(changed, "k1", mac));
  }

  @Test
  void macCoversThePublicKey() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var mac = protector.mac(key, "k1");
    var changed =
        new SigningKey(
            key.id(),
            key.algorithm(),
            pair(SigningKeyAlgorithm.ML_DSA_65).getPublic(),
            key.lifetime());

    assertFalse(protector.isIntact(changed, "k1", mac));
  }

  @Test
  void macCoversTheLifetime() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var mac = protector.mac(key, "k1");

    assertFalse(
        protector.isIntact(key.retireAt(T0.plusSeconds(120), Duration.ofMinutes(5)), "k1", mac));
  }

  @Test
  void macCoversTheRevocation() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var revoked = key.revokeAt(T0.plusSeconds(120));
    var revokedMac = protector.mac(revoked, "k1");

    // A row restored to its unrevoked state must not pass with the revoked row's MAC, nor the
    // other way round.
    assertFalse(protector.isIntact(key, "k1", revokedMac));
    assertFalse(protector.isIntact(revoked, "k1", protector.mac(key, "k1")));
    assertTrue(protector.isIntact(revoked, "k1", revokedMac));
  }

  @Test
  void macIgnoresSubMicrosecondDifferences() {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, ML_DSA);
    var revokedAtNanos = key.revokeAt(T0.plusSeconds(120).plusNanos(999));
    var revokedAtMicros = key.revokeAt(T0.plusSeconds(120));

    assertTrue(protector.isIntact(revokedAtMicros, "k1", protector.mac(revokedAtNanos, "k1")));
  }
}
