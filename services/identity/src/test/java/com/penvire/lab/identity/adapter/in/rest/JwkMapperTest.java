package com.penvire.lab.identity.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwkMapperTest {

  private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
  private static final SigningKeyLifetime LIFETIME =
      new SigningKeyLifetime(T0, T0, T0.plusSeconds(60), T0.plusSeconds(120));

  private final JwkMapper mapper = new JwkMapperImpl();

  private static SigningKey key(SigningKeyAlgorithm algorithm, String generatorName)
      throws GeneralSecurityException {
    PublicKey publicKey = KeyPairGenerator.getInstance(generatorName).generateKeyPair().getPublic();
    return new SigningKey(UUID.randomUUID(), algorithm, publicKey, LIFETIME);
  }

  @Test
  void mapsAnMlDsa65KeyToAnAkpJwkAndBack() throws GeneralSecurityException {
    var key = key(SigningKeyAlgorithm.ML_DSA_65, "ML-DSA-65");

    var jwk = mapper.toJwk(key);

    assertAll(
        () -> assertEquals("AKP", jwk.getKty()),
        () -> assertEquals("ML-DSA-65", jwk.getAlg()),
        () -> assertEquals(key.kid(), jwk.getKid()),
        () -> assertEquals("sig", jwk.getUse()),
        () -> assertEquals(1952, Base64.getUrlDecoder().decode(jwk.getPub()).length),
        () -> assertNull(jwk.getN()),
        () -> assertNull(jwk.getE()),
        () ->
            assertArrayEquals(key.publicKey().getEncoded(), mapper.toPublicKey(jwk).getEncoded()));
  }

  @Test
  void mapsAnRs256KeyToAnRsaJwkAndBack() throws GeneralSecurityException {
    var key = key(SigningKeyAlgorithm.RS256, "RSA");

    var jwk = mapper.toJwk(key);

    assertAll(
        () -> assertEquals("RSA", jwk.getKty()),
        () -> assertEquals("RS256", jwk.getAlg()),
        () -> assertEquals(key.kid(), jwk.getKid()),
        () -> assertEquals("sig", jwk.getUse()),
        () -> assertNull(jwk.getPub()),
        () ->
            assertArrayEquals(key.publicKey().getEncoded(), mapper.toPublicKey(jwk).getEncoded()));
  }

  @Test
  void rejectsAnAkpJwkWithAMismatchingAlg() throws GeneralSecurityException {
    var jwk = mapper.toJwk(key(SigningKeyAlgorithm.ML_DSA_65, "ML-DSA-65")).alg("RS256");

    assertThrows(IllegalArgumentException.class, () -> mapper.toPublicKey(jwk));
  }

  @Test
  void rejectsAnRsaJwkWithAMismatchingAlg() throws GeneralSecurityException {
    var jwk = mapper.toJwk(key(SigningKeyAlgorithm.RS256, "RSA")).alg("ML-DSA-65");

    assertThrows(IllegalArgumentException.class, () -> mapper.toPublicKey(jwk));
  }

  @Test
  void rejectsAnAkpJwkWithAPubOfTheWrongLength() throws GeneralSecurityException {
    var jwk = mapper.toJwk(key(SigningKeyAlgorithm.ML_DSA_65, "ML-DSA-65"));
    jwk.setPub(Objects.requireNonNull(jwk.getPub()).substring(4));

    assertThrows(IllegalArgumentException.class, () -> mapper.toPublicKey(jwk));
  }

  @Test
  void rejectsPaddedBase64() throws GeneralSecurityException {
    var jwk = mapper.toJwk(key(SigningKeyAlgorithm.RS256, "RSA"));
    jwk.setE(Base64.getUrlEncoder().encodeToString(new byte[] {1, 0}));

    assertThrows(IllegalArgumentException.class, () -> mapper.toPublicKey(jwk));
  }

  @Test
  void rejectsAnUnknownKeyType() throws GeneralSecurityException {
    var jwk = mapper.toJwk(key(SigningKeyAlgorithm.RS256, "RSA")).kty("EC");

    assertThrows(IllegalArgumentException.class, () -> mapper.toPublicKey(jwk));
  }
}
