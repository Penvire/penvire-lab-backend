package com.penvire.lab.identity.adapter.out.crypto;

import static com.penvire.lab.identity.adapter.out.crypto.TestKeys.T0;
import static com.penvire.lab.identity.adapter.out.crypto.TestKeys.key;
import static com.penvire.lab.identity.adapter.out.crypto.TestKeys.pair;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SigningKeyEncodingTest {

  private static final SigningKey KEY =
      key(SigningKeyAlgorithm.ML_DSA_65, pair(SigningKeyAlgorithm.ML_DSA_65));

  @Test
  void encodesTheSameKeyAlike() {
    assertArrayEquals(
        SigningKeyEncoding.associatedData(KEY, "k1"), SigningKeyEncoding.associatedData(KEY, "k1"));
    assertArrayEquals(SigningKeyEncoding.macInput(KEY), SigningKeyEncoding.macInput(KEY));
  }

  @Test
  void bindsTheAssociatedDataToTheKek() {
    assertFalse(
        Arrays.equals(
            SigningKeyEncoding.associatedData(KEY, "k1"),
            SigningKeyEncoding.associatedData(KEY, "k2")));
  }

  @Test
  void separatesTheAssociatedDataFromTheMacInput() {
    assertFalse(
        Arrays.equals(
            SigningKeyEncoding.associatedData(KEY, "k1"), SigningKeyEncoding.macInput(KEY)));
  }

  @Test
  void distinguishesARevokedKeyFromAnUnrevokedOne() {
    assertFalse(
        Arrays.equals(
            SigningKeyEncoding.macInput(KEY),
            SigningKeyEncoding.macInput(KEY.revokeAt(T0.plusSeconds(120)))));
  }

  @Test
  void distinguishesInstantsOneMicrosecondApart() {
    var lifetime = KEY.lifetime();
    var later =
        new SigningKeyLifetime(
            lifetime.publishFrom(),
            lifetime.signFrom(),
            lifetime.signUntil().plusNanos(1_000),
            lifetime.publishUntil());
    var changed = new SigningKey(KEY.id(), KEY.algorithm(), KEY.publicKey(), later);

    assertFalse(
        Arrays.equals(SigningKeyEncoding.macInput(KEY), SigningKeyEncoding.macInput(changed)));
  }
}
