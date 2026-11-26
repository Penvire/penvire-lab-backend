package com.penvire.lab.identity.adapter.out.crypto.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.domain.account.PasswordHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Argon2PasswordHasherAdapterTest {

  private final Argon2PasswordHasherAdapter hasher = new Argon2PasswordHasherAdapter();

  @Test
  void hashesAndVerifiesMatchingPassword() {
    PasswordHash hash = hasher.hash("correct-horse-battery-staple");

    assertTrue(hash.value().startsWith("$argon2id$v=19$"));
    assertTrue(hasher.verify("correct-horse-battery-staple", hash));
  }

  @Test
  void rejectsWrongPassword() {
    PasswordHash hash = hasher.hash("correct-horse-battery-staple");

    assertFalse(hasher.verify("wrong-horse-battery-staple", hash));
  }

  @Test
  void producesDistinctSaltsForIdenticalPasswords() {
    PasswordHash first = hasher.hash("correct-horse-battery-staple");
    PasswordHash second = hasher.hash("correct-horse-battery-staple");

    assertNotEquals(first.value(), second.value());
    assertTrue(hasher.verify("correct-horse-battery-staple", first));
    assertTrue(hasher.verify("correct-horse-battery-staple", second));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-a-phc-string",
        "argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA",
        "$bcrypt$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA",
        "$argon2id$v=16$m=19456,t=2,p=1$c2FsdA$aGFzaA",
        "$argon2id$v=bad$m=19456,t=2,p=1$c2FsdA$aGFzaA",
        "$argon2id$v=19$m=0,t=2,p=1$c2FsdA$aGFzaA",
        "$argon2id$v=19$m=19456,t=0,p=1$c2FsdA$aGFzaA",
        "$argon2id$v=19$m=19456,t=2,p=0$c2FsdA$aGFzaA",
        "$argon2id$v=19$m=19456,t=2$c2FsdA$aGFzaA",
        "$argon2id$v=19$m=19456,t=2,p=1$!!!not-base64!!!$aGFzaA",
        "$argon2id$v=19$m=19456,t=2,p=1$$aGFzaA",
        "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$",
      })
  void rejectsMalformedStoredHashes(String malformed) {
    assertFalse(hasher.verify("correct-horse-battery-staple", new PasswordHash(malformed)));
  }
}
