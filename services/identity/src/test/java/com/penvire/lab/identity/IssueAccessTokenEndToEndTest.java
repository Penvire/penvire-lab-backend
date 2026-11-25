package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntityRepository;
import com.penvire.lab.identity.application.port.in.IssueAccessTokenUseCase;
import com.penvire.lab.identity.application.port.out.SigningKeyPersistencePort;
import com.penvire.lab.identity.config.IdentityConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import com.penvire.lab.jwt.JwtType;
import com.penvire.lab.jwt.JwtValidationPolicy;
import com.penvire.lab.jwt.JwtValidator;
import com.penvire.lab.jwt.JwtVerificationException;
import com.penvire.lab.jwt.jws.MLDSA65JwsVerifier;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Identity issues an access token with the stored key that signs now, and a consumer verifies it
 * with nothing but the public key from the JWKS endpoint.
 */
@QuarkusTest
class IssueAccessTokenEndToEndTest {

  private static final String SUBJECT = "test-user:alice";
  private static final String CLIENT_ID = "test-client";
  private static final String AUDIENCE = "evidence-api";
  private static final int ML_DSA_65_PUBLIC_KEY_BYTES = 1952;

  @Inject IssueAccessTokenUseCase issueAccessToken;
  @Inject SigningKeyPersistencePort keys;
  @Inject SigningKeyEntityRepository entities;
  @Inject IdentityConfig identityConfig;
  @Inject Clock clock;

  @BeforeEach
  @AfterEach
  void emptyTheTable() {
    QuarkusTransaction.requiringNew().run(() -> entities.deleteAll());
  }

  @Test
  void aConsumerVerifiesTheIssuedTokenWithTheKeyFromTheJwks() {
    var now = clock.instant();
    var signer = store(generate(), signingAround(now));
    store(generate(), pendingAfter(now));

    var token = issueAccessToken.issueAccessToken(SUBJECT, CLIENT_ID, AUDIENCE);
    var claims = consumer().validate(token, policy()).claims();

    assertEquals(identityConfig.issuer().toString(), claims.path("iss").textValue());
    assertEquals(SUBJECT, claims.path("sub").textValue());
    assertEquals(CLIENT_ID, claims.path("client_id").textValue());
    assertEquals(AUDIENCE, claims.path("aud").textValue());
    assertEquals(signer.kid(), header(token).get("kid"));
  }

  @Test
  void aTokenIsNotVerifiedWithAnotherPublishedKey() {
    var now = clock.instant();
    store(generate(), signingAround(now));
    var pending = store(generate(), pendingAfter(now));
    var token = issueAccessToken.issueAccessToken(SUBJECT, CLIENT_ID, AUDIENCE);
    var jwks = jwks();
    var validator =
        new JwtValidator(
            Map.of("ML-DSA-65", new MLDSA65JwsVerifier()),
            clock,
            (issuer, keyId, algorithm, type) -> publicKey(jwks, pending.kid()));

    assertThrows(JwtVerificationException.class, () -> validator.validate(token, policy()));
  }

  @Test
  void refusesToIssueWithoutAKeyThatSignsNow() {
    store(generate(), pendingAfter(clock.instant()));

    assertThrows(
        IllegalStateException.class,
        () -> issueAccessToken.issueAccessToken(SUBJECT, CLIENT_ID, AUDIENCE));
  }

  /** A consumer that trusts only the keys the JWKS endpoint serves, looked up by kid. */
  private JwtValidator consumer() {
    var jwks = jwks();
    return new JwtValidator(
        Map.of("ML-DSA-65", new MLDSA65JwsVerifier()),
        clock,
        (issuer, keyId, algorithm, type) -> {
          assertEquals("ML-DSA-65", algorithm);
          assertEquals(JwtType.ACCESS, type);
          return publicKey(jwks, keyId);
        });
  }

  private JwtValidationPolicy policy() {
    return new JwtValidationPolicy(
        identityConfig.issuer().toString(),
        Set.of(AUDIENCE),
        Set.of(JwtType.ACCESS),
        Set.of("ML-DSA-65"),
        Duration.ofSeconds(30));
  }

  private static JsonPath jwks() {
    return given().when().get("/.well-known/jwks.json").then().statusCode(200).extract().jsonPath();
  }

  /**
   * Rebuilds the public key from the AKP JWK's raw {@code pub} bytes. An ML-DSA-65 SPKI is a fixed
   * prefix followed by those bytes, so the prefix is taken from any other ML-DSA-65 key.
   */
  private static PublicKey publicKey(JsonPath jwks, String kid) {
    Map<String, Object> jwk = jwks.getMap("keys.find { it.kid == '" + kid + "' }");
    if (jwk == null) {
      throw new JwtVerificationException("The JWKS has no key " + kid);
    }
    assertEquals("AKP", jwk.get("kty"));
    assertEquals("ML-DSA-65", jwk.get("alg"));

    var raw = Base64.getUrlDecoder().decode((String) jwk.get("pub"));
    var template = generate().getPublic().getEncoded();
    var spki = Arrays.copyOf(template, template.length);
    System.arraycopy(raw, 0, spki, template.length - ML_DSA_65_PUBLIC_KEY_BYTES, raw.length);
    try {
      return KeyFactory.getInstance("ML-DSA").generatePublic(new X509EncodedKeySpec(spki));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, Object> header(String token) {
    var json =
        new String(
            Base64.getUrlDecoder().decode(token.split("[.]", -1)[0]), StandardCharsets.UTF_8);
    return JsonPath.from(json).getMap("");
  }

  private SigningKey store(KeyPair pair, SigningKeyLifetime lifetime) {
    var key =
        new SigningKey(
            UUID.randomUUID(), SigningKeyAlgorithm.ML_DSA_65, pair.getPublic(), lifetime);
    QuarkusTransaction.requiringNew().run(() -> keys.insert(key, pair.getPrivate()));
    return key;
  }

  private static SigningKeyLifetime signingAround(Instant now) {
    return new SigningKeyLifetime(
        now.minus(Duration.ofHours(1)),
        now.minus(Duration.ofMinutes(30)),
        now.plus(Duration.ofHours(1)),
        now.plus(Duration.ofHours(2)));
  }

  /** Published now, signs only from an hour on. */
  private static SigningKeyLifetime pendingAfter(Instant now) {
    return new SigningKeyLifetime(
        now.minus(Duration.ofMinutes(1)),
        now.plus(Duration.ofHours(1)),
        now.plus(Duration.ofDays(30)),
        now.plus(Duration.ofDays(31)));
  }

  private static KeyPair generate() {
    try {
      return KeyPairGenerator.getInstance("ML-DSA-65").generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
