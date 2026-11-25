package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.penvire.lab.identity.adapter.out.crypto.AccessTokenSignerAdapter;
import com.penvire.lab.identity.adapter.out.crypto.Signer;
import com.penvire.lab.identity.application.port.out.TokenSignerPort;
import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import java.security.KeyPairGenerator;
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
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final TestJwtConfig JWT_CONFIG = new TestJwtConfig(Duration.ofMinutes(5));
  private static final int MAX_CLAIM_CHARS = 4_096;
  private static final ActiveSigningKey SIGNER =
      TestSigningKeys.active(
          SigningKeyAlgorithm.ML_DSA_65, TestSigningKeys.pair(SigningKeyAlgorithm.ML_DSA_65), NOW);

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
  void rejectsACombinedPayloadThatExceedsTheVerifierByteLimit() {
    // Each claim fits the character limit, but together they exceed 16 KiB of UTF-8.
    var claim = "é".repeat(MAX_CLAIM_CHARS);
    var issuer = issuer(new AccessTokenSignerAdapter(new Signer()));

    assertThrows(
        IllegalArgumentException.class, () -> issuer.issueAccessToken(claim, claim, claim));
  }

  @ParameterizedTest
  @ValueSource(strings = {"RSA", "ML-DSA-44"})
  void refusesToSignWithAPrivateKeyThatDoesNotMatchTheKeysAlgorithm(String algorithm)
      throws Exception {
    var wrongPrivateKey = KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPrivate();
    var mismatched = new ActiveSigningKey(SIGNER.key(), wrongPrivateKey);
    var signer = new AccessTokenSignerAdapter(new Signer());
    var claims = claimsWithTimes(NOW, NOW, NOW.plusSeconds(300));

    assertThrows(IllegalStateException.class, () -> signer.sign(claims, mismatched));
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
    return issuer((claims, key) -> fail("Invalid claims reached the signer"));
  }

  private static TokenService issuer(TokenSignerPort signer) {
    return new TokenService(
        JWT_CONFIG,
        new TestIdentityConfig(ISSUER),
        CLOCK,
        signer,
        TestSigningKeys.onlySigner(SIGNER));
  }

  private static AccessTokenClaims claimsWithTimes(
      Instant issuedAt, Instant notBefore, Instant expiresAt) {
    return new AccessTokenClaims(
        ISSUER, SUBJECT, CLIENT, AUDIENCE, "test-token", issuedAt, notBefore, expiresAt);
  }
}
