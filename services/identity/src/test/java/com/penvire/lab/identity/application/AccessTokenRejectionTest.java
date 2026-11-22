package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.penvire.lab.identity.adapter.out.signing.MLDsa65JwsSigner;
import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessTokenRejectionTest {

  private static final String ISSUER = "https://identity.lab.test";
  private static final String SUBJECT = "test-user:alice";
  private static final String CLIENT = "test-client";
  private static final String AUDIENCE = "evidence-api";
  private static final String KEY_ID = "test-key-1";
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final TestJwtConfig JWT_CONFIG = new TestJwtConfig(Duration.ofMinutes(5));
  private static final int MAX_CLAIM_CHARS = 4_096;

  @ParameterizedTest
  @CsvSource({
    "' ',test-client,evidence-api",
    "test-user:alice,' ',evidence-api",
    "test-user:alice,test-client,' '"
  })
  void rejectsBlankIssuanceInputsBeforeCallingTheSigner(
      String subject, String client, String audience) {
    assertThrows(
        IllegalArgumentException.class,
        () -> issuerWithUnreachableSigner().issueAccessToken(subject, client, audience));
  }

  @Test
  void rejectsAnOversizedClaimBeforeCallingTheSigner() {
    var subject = "a".repeat(MAX_CLAIM_CHARS + 1);

    assertThrows(
        IllegalArgumentException.class,
        () -> issuerWithUnreachableSigner().issueAccessToken(subject, CLIENT, AUDIENCE));
  }

  @Test
  void rejectsACombinedPayloadThatExceedsTheVerifierByteLimit() throws Exception {
    // Each claim fits the character limit, but together they exceed 16 KiB of UTF-8.
    var claim = "é".repeat(MAX_CLAIM_CHARS);
    var issuer =
        new TokenService(JWT_CONFIG, ISSUER, CLOCK, new MLDsa65JwsSigner(KEY_ID, signingKey()));

    assertThrows(
        IllegalArgumentException.class, () -> issuer.issueAccessToken(claim, claim, claim));
  }

  @Test
  void rejectsAKeyIdThatMakesTheHeaderExceedTheVerifierByteLimit() throws Exception {
    // The key ID alone uses 2 KiB. With the other fields, the header cannot fit.
    var oversizedKeyId = "k".repeat(2 * 1024);
    var key = signingKey();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          var signer = new MLDsa65JwsSigner(oversizedKeyId, key);
          new TokenService(JWT_CONFIG, ISSUER, CLOCK, signer)
              .issueAccessToken(SUBJECT, CLIENT, AUDIENCE);
        });
  }

  @Test
  void rejectsBlankSigningKeyId() throws Exception {
    var key = signingKey();

    assertThrows(IllegalArgumentException.class, () -> new MLDsa65JwsSigner(" ", key));
  }

  @ParameterizedTest
  @ValueSource(strings = {"RSA", "ML-DSA-44"})
  void rejectsPrivateKeysOutsideTheRequiredParameterSet(String algorithm) throws Exception {
    var key = KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPrivate();

    assertThrows(IllegalArgumentException.class, () -> new MLDsa65JwsSigner(KEY_ID, key));
  }

  @Test
  void rejectsExpiryAtIssuanceEvenWhenNotBeforeIsEarlier() {
    assertThrows(
        IllegalArgumentException.class, () -> claimsWithTimes(NOW, NOW.minusSeconds(1), NOW));
  }

  @Test
  void rejectsExpiryAtNotBeforeEvenWhenIssuedEarlier() {
    assertThrows(
        IllegalArgumentException.class, () -> claimsWithTimes(NOW.minusSeconds(1), NOW, NOW));
  }

  private static TokenService issuerWithUnreachableSigner() {
    return new TokenService(
        JWT_CONFIG, ISSUER, CLOCK, claims -> fail("Invalid claims reached the signer"));
  }

  private static AccessTokenClaims claimsWithTimes(
      Instant issuedAt, Instant notBefore, Instant expiresAt) {
    return new AccessTokenClaims(
        ISSUER, SUBJECT, CLIENT, AUDIENCE, "test-token", issuedAt, notBefore, expiresAt);
  }

  private static PrivateKey signingKey() throws Exception {
    return KeyPairGenerator.getInstance("ML-DSA-65").generateKeyPair().getPrivate();
  }
}
