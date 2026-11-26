package com.penvire.lab.identity;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penvire.lab.identity.adapter.in.rest.JwkMapper;
import com.penvire.lab.identity.application.port.in.jwt.IssueAccessTokenUseCase;
import com.penvire.lab.identity.application.port.in.signingkey.RotateSigningKeyUseCase;
import com.penvire.lab.identity.config.IdentityConfig;
import com.penvire.lab.identity.config.JwksConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.server.model.Jwk;
import com.penvire.lab.identity.server.model.JwkSet;
import com.penvire.lab.jwt.JwtType;
import com.penvire.lab.jwt.JwtValidationPolicy;
import com.penvire.lab.jwt.JwtValidator;
import com.penvire.lab.jwt.JwtVerificationException;
import com.penvire.lab.jwt.jws.MLDSA65JwsVerifier;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Identity boots with an active signing key for every configured algorithm before serving traffic,
 * without waiting for the daily rotation schedule.
 */
@QuarkusTest
@TestProfile(SigningKeyStartupTest.FreshBoot.class)
class SigningKeyStartupTest {

  private static final String SUBJECT = "test-user:alice";
  private static final String CLIENT_ID = "test-client";
  private static final String AUDIENCE = "evidence-api";

  @Inject IssueAccessTokenUseCase issueAccessToken;
  @Inject RotateSigningKeyUseCase rotateSigningKey;
  @Inject IdentityConfig identityConfig;
  @Inject JwksConfig jwksConfig;
  @Inject JwkMapper jwkMapper;
  @Inject Clock clock;

  @Test
  void publishesSigningKeysForEveryConfiguredAlgorithmOnStartup() {
    var keys = jwks().getKeys();
    var expectedAlgorithms =
        jwksConfig.algorithms().stream()
            .map(SigningKeyAlgorithm::joseName)
            .collect(Collectors.toSet());

    assertEquals(expectedAlgorithms.size(), keys.size());
    assertEquals(expectedAlgorithms, keys.stream().map(Jwk::getAlg).collect(Collectors.toSet()));
  }

  @Test
  void issuesAVerifiableAccessTokenImmediatelyAfterStartup() {
    var token = issueAccessToken.issueAccessToken(SUBJECT, CLIENT_ID, AUDIENCE);
    var published = jwks();
    var validator =
        new JwtValidator(
            Map.of("ML-DSA-65", new MLDSA65JwsVerifier()),
            clock,
            (issuer, keyId, algorithm, type) -> publicKey(published, keyId));
    var policy =
        new JwtValidationPolicy(
            identityConfig.issuer().toString(),
            Set.of(AUDIENCE),
            Set.of(JwtType.ACCESS),
            Set.of("ML-DSA-65"),
            Duration.ofSeconds(30));

    var claims = validator.validate(token, policy).claims();

    assertEquals(identityConfig.issuer().toString(), claims.path("iss").textValue());
    assertEquals(SUBJECT, claims.path("sub").textValue());
    assertEquals(CLIENT_ID, claims.path("client_id").textValue());
    assertEquals(AUDIENCE, claims.path("aud").textValue());
  }

  @Test
  void rerunningRotationAfterStartupKeepsTheExistingKeys() {
    var before = kids(jwks());

    rotateSigningKey.rotate();

    assertEquals(before, kids(jwks()));
  }

  private PublicKey publicKey(JwkSet jwks, String kid) {
    return jwks.getKeys().stream()
        .filter(jwk -> kid.equals(jwk.getKid()))
        .findFirst()
        .map(jwkMapper::toPublicKey)
        .orElseThrow(() -> new JwtVerificationException("The JWKS has no key " + kid));
  }

  private static JwkSet jwks() {
    return given()
        .when()
        .get("/.well-known/jwks.json")
        .then()
        .statusCode(200)
        .extract()
        .as(JwkSet.class);
  }

  private static Set<String> kids(JwkSet jwks) {
    return jwks.getKeys().stream().map(Jwk::getKid).collect(Collectors.toSet());
  }

  /**
   * Isolated boot profile so earlier test classes' table cleanup cannot remove startup keys, while
   * keeping the 24-hour schedule delay so only {@code @Startup} can create them.
   */
  public static class FreshBoot implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "penvire.jwks.rotation-delay", "24h",
          "penvire.jwks.rotation-interval", "24h");
    }
  }
}
