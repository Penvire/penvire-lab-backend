package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.adapter.out.crypto.jwt.AccessTokenSignerAdapter;
import com.penvire.lab.identity.adapter.out.crypto.jwt.Signer;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.jwt.JwtType;
import com.penvire.lab.jwt.JwtValidationPolicy;
import com.penvire.lab.jwt.JwtValidator;
import com.penvire.lab.jwt.JwtVerificationException;
import com.penvire.lab.jwt.jws.JwsVerifier;
import com.penvire.lab.jwt.jws.MLDSA65JwsVerifier;
import com.penvire.lab.jwt.jws.Rs256JwsVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IssueAccessTokenTest {

  private static final String ISSUER = "https://identity.lab.test";
  private static final String SUBJECT = "test-user:alice";
  private static final String CLIENT_ID = "test-client";
  private static final String AUDIENCE = "evidence-api";
  private static final String ALGORITHM = "ML-DSA-65";
  private static final String RS256 = "RS256";
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final JwtValidationPolicy POLICY = policy(ALGORITHM);
  private static final JwtValidationPolicy RS256_POLICY = policy(RS256);

  private static JwtValidationPolicy policy(String algorithm) {
    return new JwtValidationPolicy(
        ISSUER,
        Set.of(AUDIENCE),
        Set.of(JwtType.ACCESS),
        Set.of(algorithm),
        Duration.ofSeconds(30));
  }

  @ParameterizedTest
  @CsvSource({"PT5M,300", "PT12M,720"})
  void issuesAnAccessTokenWithTheConfiguredLifetime(String lifetime, long expectedSeconds)
      throws Exception {
    var fixture = fixture(Duration.parse(lifetime));
    var claims = fixture.validator().validate(fixture.issue(), POLICY).claims();

    assertEquals(ISSUER, claims.path("iss").textValue());
    assertEquals(SUBJECT, claims.path("sub").textValue());
    assertEquals(CLIENT_ID, claims.path("client_id").textValue());
    assertEquals(AUDIENCE, claims.path("aud").textValue());
    assertFalse(claims.path("jti").asText().isBlank());
    assertEquals(NOW.getEpochSecond(), claims.path("iat").longValue());
    assertEquals(NOW.getEpochSecond(), claims.path("nbf").longValue());
    assertEquals(NOW.plusSeconds(expectedSeconds).getEpochSecond(), claims.path("exp").longValue());
  }

  @Test
  void issuesDistinctTokenIdsEvenWhenTimeAndInputsMatch() throws Exception {
    var fixture = fixture(Duration.ofMinutes(5));
    var first = fixture.validator().validate(fixture.issue(), POLICY).claims();
    var second = fixture.validator().validate(fixture.issue(), POLICY).claims();

    assertNotEquals(first.path("jti").textValue(), second.path("jti").textValue());
  }

  @Test
  void theIssuedTokenIsRejectedOnceItsLifetimeAndTheSkewHavePassed() throws Exception {
    var lifetime = Duration.ofMinutes(5);
    var clock = MutableClock.at(NOW);
    var fixture = fixture(lifetime, clock);
    var token = fixture.issue();

    clock.advance(lifetime.plus(POLICY.clockSkew()).minusSeconds(1));
    fixture.validator().validate(token, POLICY);

    clock.advance(Duration.ofSeconds(1));
    assertThrows(JwtVerificationException.class, () -> fixture.validator().validate(token, POLICY));
  }

  @Test
  void issuesAnRs256AccessTokenWhenRequested() throws Exception {
    var fixture = fixture(SigningKeyAlgorithm.RS256, Duration.ofMinutes(5), CLOCK);
    var token = fixture.issue(SigningKeyAlgorithm.RS256);
    var claims = fixture.validator().validate(token, RS256_POLICY).claims();

    assertEquals(ISSUER, claims.path("iss").textValue());
    assertEquals(SUBJECT, claims.path("sub").textValue());
    assertEquals(CLIENT_ID, claims.path("client_id").textValue());
    assertEquals(AUDIENCE, claims.path("aud").textValue());
    assertFalse(claims.path("jti").asText().isBlank());
    assertEquals(NOW.getEpochSecond(), claims.path("iat").longValue());
    assertEquals(NOW.getEpochSecond(), claims.path("nbf").longValue());
    assertEquals(NOW.plusSeconds(300).getEpochSecond(), claims.path("exp").longValue());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_OPENSSL_INTEROP", matches = "1")
  void openSslVerifiesTheSignatureOnTheIssuedToken(@TempDir Path directory) throws Exception {
    var fixture = fixture(Duration.ofMinutes(5));
    var parts = fixture.issue().split("[.]", -1);
    assertEquals(3, parts.length);

    var input =
        Files.writeString(
            directory.resolve("input"), parts[0] + "." + parts[1], StandardCharsets.US_ASCII);
    var signature =
        Files.write(directory.resolve("signature"), Base64.getUrlDecoder().decode(parts[2]));
    var publicKey = Files.write(directory.resolve("public.der"), fixture.publicKey().getEncoded());

    var version = runOpenSsl(directory.resolve("version.log"), "openssl", "version");
    System.out.println(version.strip());
    runOpenSsl(
        directory.resolve("verify.log"),
        "openssl",
        "pkeyutl",
        "-verify",
        "-rawin",
        "-pubin",
        "-inkey",
        publicKey.toString(),
        "-keyform",
        "DER",
        "-in",
        input.toString(),
        "-sigfile",
        signature.toString());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_OPENSSL_INTEROP", matches = "1")
  void openSslVerifiesTheSignatureOnTheIssuedRs256Token(@TempDir Path directory) throws Exception {
    var fixture = fixture(SigningKeyAlgorithm.RS256, Duration.ofMinutes(5), CLOCK);
    var parts = fixture.issue(SigningKeyAlgorithm.RS256).split("[.]", -1);
    assertEquals(3, parts.length);

    var input =
        Files.writeString(
            directory.resolve("input"), parts[0] + "." + parts[1], StandardCharsets.US_ASCII);
    var signature =
        Files.write(directory.resolve("signature"), Base64.getUrlDecoder().decode(parts[2]));
    var publicDer = Files.write(directory.resolve("public.der"), fixture.publicKey().getEncoded());
    var publicPem = directory.resolve("public.pem");

    var version = runOpenSsl(directory.resolve("version.log"), "openssl", "version");
    System.out.println(version.strip());
    runOpenSsl(
        directory.resolve("pem.log"),
        "openssl",
        "pkey",
        "-pubin",
        "-inform",
        "DER",
        "-in",
        publicDer.toString(),
        "-out",
        publicPem.toString());
    runOpenSsl(
        directory.resolve("verify.log"),
        "openssl",
        "dgst",
        "-sha256",
        "-verify",
        publicPem.toString(),
        "-signature",
        signature.toString(),
        input.toString());
  }

  private static Fixture fixture(Duration lifetime) throws Exception {
    return fixture(SigningKeyAlgorithm.ML_DSA_65, lifetime, CLOCK);
  }

  /** The use case and the validator share one clock, so moving it ages the issued token. */
  private static Fixture fixture(Duration lifetime, Clock clock) throws Exception {
    return fixture(SigningKeyAlgorithm.ML_DSA_65, lifetime, clock);
  }

  private static Fixture fixture(SigningKeyAlgorithm algorithm, Duration lifetime, Clock clock) {
    var keys = TestSigningKeys.pair(algorithm);
    var signingKey = TestSigningKeys.active(algorithm, keys, NOW);
    var useCase =
        new TokenService(
            new TestJwtConfig(lifetime),
            new TestIdentityConfig(ISSUER),
            clock,
            new AccessTokenSignerAdapter(new Signer()),
            TestSigningKeys.onlySigner(signingKey));
    JwsVerifier verifier =
        switch (algorithm) {
          case ML_DSA_65 -> new MLDSA65JwsVerifier();
          case RS256 -> new Rs256JwsVerifier();
        };
    var joseName = algorithm.joseName();
    var validator =
        new JwtValidator(
            Map.of(joseName, verifier),
            clock,
            (issuer, keyId, resolvedAlgorithm, type) -> {
              assertEquals(ISSUER, issuer);
              assertEquals(signingKey.key().kid(), keyId);
              assertEquals(joseName, resolvedAlgorithm);
              assertEquals(JwtType.ACCESS, type);
              return keys.getPublic();
            });
    return new Fixture(useCase, validator, keys.getPublic());
  }

  private static String runOpenSsl(Path log, String... command) throws Exception {
    var process =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    var finished = process.waitFor(10, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
    }
    assertTrue(finished, "OpenSSL did not finish within 10 seconds");

    var output = Files.readString(log);
    assertEquals(0, process.exitValue(), output);
    return output;
  }

  private record Fixture(TokenService useCase, JwtValidator validator, PublicKey publicKey) {
    String issue() {
      return useCase.issueAccessToken(SUBJECT, CLIENT_ID, AUDIENCE);
    }

    String issue(SigningKeyAlgorithm algorithm) {
      return useCase.issueAccessToken(SUBJECT, CLIENT_ID, AUDIENCE, algorithm);
    }
  }
}
