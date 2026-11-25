package com.penvire.lab.identity.adapter.out.crypto;

import static com.penvire.lab.identity.adapter.out.crypto.TestKeys.K1;
import static com.penvire.lab.identity.adapter.out.crypto.TestKeys.K2;
import static com.penvire.lab.identity.adapter.out.crypto.TestKeys.ring;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeyEncryptionRingTest {

  @Test
  void usesTheConfiguredCurrentKek() {
    var ring = ring("k2", Map.of("k1", K1, "k2", K2));

    assertEquals("k2", ring.current().id());
  }

  @Test
  void findsEveryConfiguredKekById() {
    var ring = ring("k2", Map.of("k1", K1, "k2", K2));

    assertAll(
        () -> assertEquals("k1", ring.getById("k1").orElseThrow().id()),
        () -> assertEquals("k2", ring.getById("k2").orElseThrow().id()),
        () -> assertTrue(ring.getById("k3").isEmpty()));
  }

  @Test
  void derivesSeparate256BitKeysForEncryptionAndMac() {
    var kek = ring("k1", Map.of("k1", K1)).current();

    assertAll(
        () -> assertEquals("AES", kek.encryption().getAlgorithm()),
        () -> assertEquals("HmacSHA256", kek.mac().getAlgorithm()),
        () -> assertEquals(32, kek.encryption().getEncoded().length),
        () -> assertEquals(32, kek.mac().getEncoded().length),
        () -> assertFalse(Arrays.equals(kek.encryption().getEncoded(), kek.mac().getEncoded())),
        () -> assertFalse(Arrays.equals(Base64.getDecoder().decode(K1), kek.mac().getEncoded())));
  }

  @Test
  void derivesTheSameKeysFromTheSameMaterial() {
    var first = ring("a", Map.of("a", K1)).current();
    var second = ring("b", Map.of("b", K1)).current();

    assertArrayEquals(first.encryption().getEncoded(), second.encryption().getEncoded());
    assertArrayEquals(first.mac().getEncoded(), second.mac().getEncoded());
  }

  @Test
  void derivesDifferentKeysFromDifferentMaterial() {
    var ring = ring("k1", Map.of("k1", K1, "k2", K2));

    assertFalse(
        Arrays.equals(
            ring.getById("k1").orElseThrow().encryption().getEncoded(),
            ring.getById("k2").orElseThrow().encryption().getEncoded()));
  }

  @Test
  void rejectsACurrentKekThatIsNotConfigured() {
    assertThrows(IllegalStateException.class, () -> ring("k2", Map.of("k1", K1)));
  }

  @Test
  void rejectsAKekThatIsNotBase64() {
    assertThrows(IllegalStateException.class, () -> ring("k1", Map.of("k1", "not base64!")));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 16, 31, 33, 64})
  void rejectsAKekThatIsNot32Bytes(int length) {
    var encoded = Base64.getEncoder().encodeToString(new byte[length]);

    assertThrows(IllegalStateException.class, () -> ring("k1", Map.of("k1", encoded)));
  }

  @Test
  void rejectsABadOlderKekEvenIfTheCurrentOneIsFine() {
    assertThrows(IllegalStateException.class, () -> ring("k1", Map.of("k1", K1, "old", "AA==")));
  }

  @Test
  void neverPrintsKeyMaterial() {
    var kek = ring("k1", Map.of("k1", K1)).current();

    assertEquals("KeyEncryptionKey[id=k1]", kek.toString());
  }
}
