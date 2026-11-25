package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntityRepository;
import com.penvire.lab.identity.application.port.out.SigningKeyPort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The JWKS endpoint serves exactly the stored keys that are published now. */
@QuarkusTest
class JwksEndpointTest {

  @Inject SigningKeyPort keys;
  @Inject SigningKeyEntityRepository entities;
  @Inject Clock clock;

  @BeforeEach
  @AfterEach
  void emptyTheTable() {
    QuarkusTransaction.requiringNew().run(() -> entities.deleteAll());
  }

  private static KeyPair generate(String algorithm) {
    try {
      return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private SigningKey store(
      SigningKeyAlgorithm algorithm, KeyPair pair, SigningKeyLifetime lifetime) {
    var key = new SigningKey(UUID.randomUUID(), algorithm, pair.getPublic(), lifetime);
    QuarkusTransaction.requiringNew().run(() -> keys.insert(key, pair.getPrivate()));
    return key;
  }

  private SigningKeyLifetime current(Instant now) {
    return new SigningKeyLifetime(
        now.minus(Duration.ofHours(1)),
        now.minus(Duration.ofMinutes(30)),
        now.plus(Duration.ofHours(1)),
        now.plus(Duration.ofHours(2)));
  }

  private static JsonPath jwks() {
    return given()
        .when()
        .get("/.well-known/jwks.json")
        .then()
        .statusCode(200)
        .contentType("application/json")
        .extract()
        .jsonPath();
  }

  @Test
  void servesAnEmptySetWithoutKeys() {
    assertEquals(List.of(), jwks().getList("keys"));
  }

  @Test
  void servesPublishedKeysOfBothAlgorithmsAsPublicJwks() {
    var now = clock.instant();
    var mlDsaPair = generate("ML-DSA-65");
    var rsaPair = generate("RSA");
    var mlDsa = store(SigningKeyAlgorithm.ML_DSA_65, mlDsaPair, current(now));
    var rsa = store(SigningKeyAlgorithm.RS256, rsaPair, current(now));

    var body = jwks();
    Map<String, Object> akp = body.getMap("keys.find { it.kid == '" + mlDsa.kid() + "' }");
    Map<String, Object> rsaJwk = body.getMap("keys.find { it.kid == '" + rsa.kid() + "' }");

    assertEquals(2, body.getList("keys").size());
    assertEquals("AKP", akp.get("kty"));
    assertEquals("ML-DSA-65", akp.get("alg"));
    assertEquals("sig", akp.get("use"));
    byte[] spki = mlDsaPair.getPublic().getEncoded();
    assertArrayEquals(
        Arrays.copyOfRange(spki, spki.length - 1952, spki.length),
        Base64.getUrlDecoder().decode((String) akp.get("pub")));
    assertEquals("RSA", rsaJwk.get("kty"));
    assertEquals("RS256", rsaJwk.get("alg"));
    var modulus = new BigInteger(1, Base64.getUrlDecoder().decode((String) rsaJwk.get("n")));
    assertEquals(((RSAPublicKey) rsaPair.getPublic()).getModulus(), modulus);
    // Nothing private, in either JWK.
    for (var jwk : List.of(akp, rsaJwk)) {
      for (var member : Set.of("d", "p", "q", "dp", "dq", "qi", "priv")) {
        assertNull(jwk.get(member), member);
      }
    }
  }

  @Test
  void leavesOutRevokedPendingAndExpiredKeys() {
    var now = clock.instant();
    var pair = generate("ML-DSA-65");
    var published = store(SigningKeyAlgorithm.ML_DSA_65, pair, current(now));
    var revoked = store(SigningKeyAlgorithm.RS256, generate("RSA"), current(now));
    QuarkusTransaction.requiringNew().run(() -> keys.revoke(revoked.id()));
    store(
        SigningKeyAlgorithm.ML_DSA_65,
        pair,
        new SigningKeyLifetime(
            now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(2)),
            now.plus(Duration.ofHours(3)), now.plus(Duration.ofHours(4))));
    store(
        SigningKeyAlgorithm.ML_DSA_65,
        pair,
        new SigningKeyLifetime(
            now.minus(Duration.ofHours(4)), now.minus(Duration.ofHours(4)),
            now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2))));

    assertEquals(List.of(published.kid()), jwks().getList("keys.kid"));
  }
}
