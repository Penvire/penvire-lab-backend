package com.penvire.lab.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.jwt.validation.JwtType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BearerAuthenticationTest {

  private static KeyPair mlDsaKeyPair;
  private static KeyPair rsaKeyPair;

  @Inject TestKeyResolver keyResolver;
  @Inject Clock clock;

  @BeforeAll
  static void generateKeys() {
    mlDsaKeyPair = TestTokenBuilder.generateMlDsa65KeyPair();
    rsaKeyPair = TestTokenBuilder.generateRsaKeyPair();
  }

  @BeforeEach
  void resetState() {
    ((MutableClock) clock).set(TestClockProducer.NOW);
    keyResolver.clear();
    keyResolver.register(
        TestTokenBuilder.ISSUER, "mldsa-1", "ML-DSA-65", JwtType.ACCESS, mlDsaKeyPair.getPublic());
    keyResolver.register(
        TestTokenBuilder.ISSUER, "rsa-1", "RS256", JwtType.ACCESS, rsaKeyPair.getPublic());
  }

  @Test
  void authenticatesValidMlDsa65TokenAndExposesPrincipalFields() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("mldsa-1")
            .subject("user-42")
            .clientId("bff-web")
            .tokenId("tok-99")
            .audiences(List.of("test-api", "other-api"))
            .scope("evidence:read engagement:write")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(200)
        .body(
            equalTo(
                "user-42|https://id.penvire.test|bff-web|tok-99|"
                    + "other-api,test-api|engagement:write,evidence:read"));
  }

  @Test
  void authenticatesValidRs256TokenWithoutScopeClaim() {
    String token =
        new TestTokenBuilder()
            .algorithm("RS256")
            .keyId("rsa-1")
            .subject("service-1")
            .clientId("worker")
            .tokenId("tok-rsa")
            .sign(rsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(200)
        .body(equalTo("service-1|https://id.penvire.test|worker|tok-rsa|test-api|"));
  }

  @Test
  void acceptsCaseInsensitiveBearerScheme() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("mldsa-1")
            .subject("user-case")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "bearer " + token)
        .when()
        .get("/test/public")
        .then()
        .statusCode(200)
        .body(equalTo("user-case"));
  }

  @Test
  void rejectsMissingHeaderOnProtectedEndpointWithBearerChallenge() {
    given()
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401)
        .header("WWW-Authenticate", containsString("Bearer"));
  }

  @Test
  void allowsAnonymousAccessOnPublicEndpointWithoutAuthorizationHeader() {
    given().when().get("/test/public").then().statusCode(200).body(equalTo("anonymous"));
  }

  @Test
  void treatsNonBearerAuthorizationAsAnonymous() {
    given()
        .header("Authorization", "Basic dXNlcjpwYXNz")
        .when()
        .get("/test/public")
        .then()
        .statusCode(200)
        .body(equalTo("anonymous"));

    given()
        .header("Authorization", "Basic dXNlcjpwYXNz")
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401);
  }

  @Test
  void rejectsEmptyBearerTokenEvenOnPublicEndpoint() {
    given().header("Authorization", "Bearer   ").when().get("/test/public").then().statusCode(401);
  }

  @Test
  void rejectsInvalidSignatureEvenOnPublicEndpoint() {
    KeyPair untrusted = TestTokenBuilder.generateMlDsa65KeyPair();
    String token =
        new TestTokenBuilder().algorithm("ML-DSA-65").keyId("mldsa-1").sign(untrusted.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/public")
        .then()
        .statusCode(401);
  }

  @Test
  void enforcesDefaultClockSkewOfThirtySeconds() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("mldsa-1")
            .expiresAt(TestClockProducer.NOW.plusSeconds(300))
            .sign(mlDsaKeyPair.getPrivate());

    // 29s past exp: still within 30s clock skew.
    ((MutableClock) clock).advance(Duration.ofSeconds(329));
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(200);

    // 30s past exp: rejected.
    ((MutableClock) clock).advance(Duration.ofSeconds(1));
    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401);
  }

  @Test
  void rejectsWrongAudience() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("mldsa-1")
            .audience("wrong-api")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401);
  }

  @Test
  void rejectsWrongIssuer() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("mldsa-1")
            .issuer("https://evil.example")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401);
  }

  @Test
  void rejectsUnknownKeyId() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("unknown-kid")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401);
  }

  @Test
  void rejectsRefreshTokenType() {
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .type("refresh+jwt")
            .keyId("mldsa-1")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(401);
  }

  @Test
  void returns503WhenJwksIsUnavailable() {
    keyResolver.setUnavailable(true);
    String token =
        new TestTokenBuilder()
            .algorithm("ML-DSA-65")
            .keyId("mldsa-1")
            .sign(mlDsaKeyPair.getPrivate());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/test/protected")
        .then()
        .statusCode(503);
  }
}
