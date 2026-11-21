package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.adapter.out.signing.MLDsa65JwsSigner;
import com.penvire.lab.jwt.JwtType;
import com.penvire.lab.jwt.JwtValidationPolicy;
import com.penvire.lab.jwt.JwtValidator;
import com.penvire.lab.jwt.jws.MLDSA65JwsVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
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

class IssueAccessTokenTest {

  private static final String ISSUER = "https://identity.lab.test";
  private static final String SUBJECT = "test-user:alice";
  private static final String CLIENT_ID = "test-client";
  private static final String AUDIENCE = "evidence-api";
  private static final String KEY_ID = "test-key-1";
  private static final String ALGORITHM = "ML-DSA-65";
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final JwtValidationPolicy POLICY =
      new JwtValidationPolicy(
          ISSUER,
          Set.of(AUDIENCE),
          Set.of(JwtType.ACCESS),
          Set.of(ALGORITHM),
          Duration.ofSeconds(30));

  @Test
  void issuesAnAccessTokenForTheIntendedSubjectAndAudience() throws Exception {
    var fixture = fixture();
    var claims = fixture.validator().validate(fixture.issue(), POLICY).claims();

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
  void issuesDistinctTokenIdsEvenWhenTimeAndInputsMatch() throws Exception {
    var fixture = fixture();
    var first = fixture.validator().validate(fixture.issue(), POLICY).claims();
    var second = fixture.validator().validate(fixture.issue(), POLICY).claims();

    assertNotEquals(first.path("jti").textValue(), second.path("jti").textValue());
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "RUN_OPENSSL_INTEROP", matches = "1")
  void openSslVerifiesTheSignatureOnTheIssuedToken(@TempDir Path directory) throws Exception {
    var fixture = fixture();
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

  private static Fixture fixture() throws Exception {
    var keys = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
    var signer = new MLDsa65JwsSigner(KEY_ID, keys.getPrivate());
    var useCase = new IssueAccessToken(ISSUER, CLOCK, signer);
    var validator =
        new JwtValidator(
            Map.of(ALGORITHM, new MLDSA65JwsVerifier()),
            CLOCK,
            (issuer, keyId, algorithm, type) -> {
              assertEquals(ISSUER, issuer);
              assertEquals(KEY_ID, keyId);
              assertEquals(ALGORITHM, algorithm);
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

  private record Fixture(IssueAccessToken useCase, JwtValidator validator, PublicKey publicKey) {
    String issue() {
      return useCase.issue(SUBJECT, CLIENT_ID, AUDIENCE);
    }
  }
}
