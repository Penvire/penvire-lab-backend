package com.penvire.lab.identity.adapter.out.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.identity.application.port.out.SigningKeyProtectorPort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class AesGcmSigningKeyProtectorAdapterTest {
  @Inject SigningKeyProtectorPort protector;

  @ParameterizedTest
  @CsvSource({"RS256, RSA", "ML_DSA_65, ML-DSA-65"})
  void onlySealsThePrivateKeyCorrespondingToThePublicKey(
      SigningKeyAlgorithm algorithm, String generatorName) throws NoSuchAlgorithmException {
    var generator = KeyPairGenerator.getInstance(generatorName);
    var pair = generator.generateKeyPair();
    var otherPair = generator.generateKeyPair();
    var now = Instant.EPOCH;
    var lifetime = new SigningKeyLifetime(now, now, now.plusSeconds(1), now.plusSeconds(2));
    var key = new SigningKey(UUID.randomUUID(), algorithm, pair.getPublic(), lifetime);

    assertThrows(IllegalArgumentException.class, () -> protector.seal(key, otherPair.getPrivate()));
    var sealed = protector.seal(key, pair.getPrivate());
    assertArrayEquals(pair.getPrivate().getEncoded(), protector.unseal(key, sealed).getEncoded());
  }
}
