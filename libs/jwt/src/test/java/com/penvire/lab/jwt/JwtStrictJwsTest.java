package com.penvire.lab.jwt;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.jwt.jws.JwsVerifier;
import com.penvire.lab.jwt.jws.MLDSA65JwsVerifier;
import com.penvire.lab.jwt.jws.Rs256JwsVerifier;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtStrictJwsTest {
  private static final String ISSUER = "https://issuer.example";
  private static final String AUDIENCE = "fixture-api";
  private static final String KID = "ml-dsa-65-test";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:01:00Z"), ZoneOffset.UTC);
  private static final String CLAIMS =
      """
      { "sub": "fixture-user", "iss": "https://issuer.example", "aud": "fixture-api",
        "jti": "mldsa-1", "client_id": "fixture-client", "iat": 1767225600,
        "nbf": 1767225600, "exp": 1767225900 }
      """;
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final JwtValidationPolicy POLICY =
      new JwtValidationPolicy(
          ISSUER,
          Set.of(AUDIENCE),
          Set.of(JwtType.ACCESS),
          Set.of("RS256", "ML-DSA-65"),
          Duration.ZERO);

  @Test
  void verifiesTheIndependentOpenSslFixtureThroughThePublicBoundary() throws Exception {
    String token = new String(resource("ml-dsa-65.jwt"), US_ASCII);
    PublicKey key =
        KeyFactory.getInstance("ML-DSA-65")
            .generatePublic(new X509EncodedKeySpec(resource("ml-dsa-65-public.der")));

    assertAuthenticated(validator(Algorithm.ML_DSA_65, key), token);
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void verifiesTheOriginalHeaderAndPayloadSegments(Algorithm algorithm) throws Exception {
    var validator = validator(algorithm, algorithm.keys().getPublic());
    String token = sign(algorithm, header(algorithm), CLAIMS);
    String[] parts = token.split("\\.");

    assertAuthenticated(validator, token);
    for (int segment : new int[] {0, 1}) {
      String json = new String(DECODER.decode(parts[segment]), UTF_8);
      String normalized = JwtTestTokens.json(json).toString();
      assertNotEquals(json, normalized);
      assertRejected(validator, replacePart(parts, segment, encode(normalized)));
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void rejectsWrongKeysAndInvalidSignaturesWithoutFallback(Algorithm algorithm) throws Exception {
    String token = sign(algorithm, header(algorithm), CLAIMS);
    var validator = validator(algorithm, algorithm.keys().getPublic());
    String[] parts = token.split("\\.");
    byte[] signature = DECODER.decode(parts[2]);

    assertRejected(
        validator,
        replacePart(
            parts, 2, ENCODER.encodeToString(Arrays.copyOf(signature, signature.length - 1))));
    signature[0] ^= 1;
    assertRejected(validator, replacePart(parts, 2, ENCODER.encodeToString(signature)));
    assertRejected(validator(algorithm, algorithm.other().keys().getPublic()), token);

    var generator = KeyPairGenerator.getInstance(algorithm.keyAlgorithm);
    if (algorithm == Algorithm.RS256) {
      generator.initialize(2048);
    }
    assertRejected(validator(algorithm, generator.generateKeyPair().getPublic()), token);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ML-DSA-44", "ML-DSA-87"})
  void rejectsOtherMlDsaParameterSetsWithAFullLengthSignature(String parameterSet)
      throws Exception {
    PublicKey wrongKey = KeyPairGenerator.getInstance(parameterSet).generateKeyPair().getPublic();
    String token = new String(resource("ml-dsa-65.jwt"), US_ASCII);

    assertRejected(validator(Algorithm.ML_DSA_65, wrongKey), token);
  }

  @ParameterizedTest
  @ValueSource(strings = {"none", "HS256", "rs256", "ml-dsa-65", "ML-DSA-44", "ML-DSA-87"})
  void rejectsAnUnconfiguredAlgorithmEvenWhenAllowedByPolicy(String algorithm) throws Exception {
    var policy =
        new JwtValidationPolicy(
            ISSUER, Set.of(AUDIENCE), Set.of(JwtType.ACCESS), Set.of(algorithm), Duration.ZERO);
    String token =
        sign(Algorithm.RS256, header(Algorithm.RS256).replace("RS256", algorithm), CLAIMS);

    assertThrows(JwtVerificationException.class, () -> beforeKeyLookup().validate(token, policy));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void rejectsAnAlgorithmOrTokenTypeOutsideTheSuppliedKeysMetadata(Algorithm algorithm)
      throws Exception {
    String access = sign(algorithm, header(algorithm), CLAIMS);
    PublicKey key = algorithm.keys().getPublic();
    var wrongAlgorithmResolver = trustedKey(key, algorithm.other(), JwtType.ACCESS);
    assertRejected(validator(algorithm, wrongAlgorithmResolver), access);

    String refresh =
        sign(
            algorithm,
            header(algorithm).replace("at+jwt", "rt+jwt"),
            addMember(CLAIMS.strip(), "\"sid\":\"session\",\"family_id\":\"family\""));
    var bothTypes =
        new JwtValidationPolicy(
            ISSUER,
            Set.of(AUDIENCE),
            Set.of(JwtType.ACCESS, JwtType.REFRESH),
            Set.of("RS256", "ML-DSA-65"),
            Duration.ZERO);
    var accessOnly = validator(algorithm, trustedKey(key, algorithm, JwtType.ACCESS));
    var refreshAllowed = validator(algorithm, trustedKey(key, algorithm, JwtType.REFRESH));

    assertEquals(
        "fixture-user", refreshAllowed.validate(refresh, bothTypes).claims().path("sub").asText());
    assertThrows(JwtVerificationException.class, () -> accessOnly.validate(refresh, bothTypes));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void rejectsMalformedCompactTokensBeforeResolvingKeys(Algorithm algorithm) throws Exception {
    String token = sign(algorithm, header(algorithm), CLAIMS);
    String[] parts = token.split("\\.");
    var validator = beforeKeyLookup();

    for (String malformed :
        new String[] {"", " ", parts[0], parts[0] + "." + parts[1], token + ".x"}) {
      assertRejected(validator, malformed);
    }
    for (int segment = 0; segment < parts.length; segment++) {
      for (String malformed : new String[] {"", "A", "AB", "AA==", "A+A", "A/A", "AA\n"}) {
        // AB has nonzero pad bits; re-encoding its decoded byte yields AA.
        assertRejected(validator, replacePart(parts, segment, malformed));
      }
      assertRejected(validator, replacePart(parts, segment, parts[segment] + "="));
    }
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void rejectsInvalidHeaderJsonAndUnsupportedHeaderModes(Algorithm algorithm) throws Exception {
    String header = header(algorithm);
    var validator = beforeKeyLookup();
    for (String invalid :
        new String[] {
          "{",
          "[]",
          header + " {}",
          addMember(header, "\"alg\":\"" + algorithm.jose + "\""),
          addMember(header, "\"ext\":{\"value\":1,\"value\":2}"),
          addMember(header, "\"crit\":[\"ext\"],\"ext\":true"),
          addMember(header, "\"crit\":[]"),
          addMember(header, "\"crit\":null"),
          addMember(header, "\"b64\":false"),
          addMember(header, "\"b64\":\"true\""),
          addMember(header, "\"b64\":null")
        }) {
      assertRejected(validator, sign(algorithm, invalid, CLAIMS));
    }

    assertRejected(validator, signEncoded(algorithm, invalidUtf8(header), encode(CLAIMS)));
    assertAuthenticated(
        validator(algorithm, algorithm.keys().getPublic()),
        sign(algorithm, addMember(header, "\"b64\":true"), CLAIMS));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void rejectsMalformedClaimsEvenWhenTheSignatureIsValid(Algorithm algorithm) throws Exception {
    var validator = validator(algorithm, algorithm.keys().getPublic());
    String header = header(algorithm);
    for (String invalid :
        new String[] {
          "{",
          "[]",
          "null",
          CLAIMS + " {}",
          addMember(CLAIMS.strip(), "\"sub\":\"fixture-user\""),
          addMember(CLAIMS.strip(), "\"ext\":{\"value\":1,\"value\":2}")
        }) {
      assertRejected(validator, sign(algorithm, header, invalid));
    }
    assertRejected(validator, signEncoded(algorithm, encode(header), invalidUtf8(CLAIMS)));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void enforcesCompactAndDecodedSizeLimits(Algorithm algorithm) throws Exception {
    var validator = validator(algorithm, algorithm.keys().getPublic());
    String header = header(algorithm);
    String[] parts = sign(algorithm, header, CLAIMS).split("\\.");
    assertAll(
        () ->
            assertAuthenticated(
                validator, sign(algorithm, padTo(header, 2048), padTo(CLAIMS, 16384))),
        () -> assertRejected(beforeKeyLookup(), sign(algorithm, padTo(header, 2049), CLAIMS)),
        () -> assertRejected(beforeKeyLookup(), sign(algorithm, header, padTo(CLAIMS, 16385))),
        () -> {
          String atLimit = replacePart(parts, 2, ENCODER.encodeToString(new byte[4096]));
          var invalidSignature =
              assertThrows(
                  JwtVerificationException.class, () -> validator.validate(atLimit, POLICY));
          assertEquals("Invalid JWT signature", invalidSignature.getMessage());
        },
        () ->
            assertRejected(
                beforeKeyLookup(), replacePart(parts, 2, ENCODER.encodeToString(new byte[4097]))),
        () -> {
          var overLimit =
              assertThrows(
                  JwtVerificationException.class,
                  () -> validator.validate("A".repeat(32769), POLICY));
          assertEquals("JWT is too large", overLimit.getMessage());
        });
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void boundsJsonDepthNamesStringsAndNumbers(Algorithm algorithm) throws Exception {
    var validator = validator(algorithm, algorithm.keys().getPublic());
    String header = header(algorithm);
    String claims = CLAIMS.strip();
    for (String member :
        new String[] {
          nestedMember(15),
          "\"" + "a".repeat(128) + "\":true",
          "\"ext\":" + "1".repeat(64),
          "\"ext\":\"" + "a".repeat(4096) + "\""
        }) {
      assertAuthenticated(validator, sign(algorithm, header, addMember(claims, member)));
    }
    assertAll(
        () -> assertJsonLimit(algorithm, nestedMember(16)),
        () -> assertJsonLimit(algorithm, "\"" + "a".repeat(129) + "\":true"),
        () -> assertJsonLimit(algorithm, "\"ext\":" + "1".repeat(65)),
        () ->
            assertRejected(
                validator,
                sign(
                    algorithm, header, addMember(claims, "\"ext\":\"" + "a".repeat(4097) + "\""))));
  }

  private static void assertJsonLimit(Algorithm algorithm, String member)
      throws GeneralSecurityException {
    var validator = validator(algorithm, algorithm.keys().getPublic());
    String header = header(algorithm);
    assertAll(
        () -> assertRejected(validator, sign(algorithm, header, addMember(CLAIMS.strip(), member))),
        () ->
            assertRejected(beforeKeyLookup(), sign(algorithm, addMember(header, member), CLAIMS)));
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void embeddedKeysAndHeaderUrlsDoNotEstablishTrustOrTriggerRequests(Algorithm algorithm)
      throws Exception {
    var requests = new AtomicInteger();
    InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          try (exchange) {
            exchange.sendResponseHeaders(404, -1);
          }
        });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort();
      var validator =
          validator(
              algorithm,
              (issuer, kid, alg, type) -> {
                throw new JwtVerificationException("No supplied key");
              });
      for (String member :
          new String[] {
            "\"jwk\":" + publicJwk(algorithm),
            "\"jku\":\"" + url + "/jwks\"",
            "\"x5u\":\"" + url + "/cert\"",
            "\"x5c\":[\"AA==\"]"
          }) {
        assertRejected(validator, sign(algorithm, addMember(header(algorithm), member), CLAIMS));
      }
      assertEquals(0, requests.get());
    } finally {
      server.stop(0);
    }
  }

  private static JwtValidator validator(Algorithm algorithm, PublicKey publicKey) {
    return validator(
        algorithm,
        (issuer, kid, alg, type) -> {
          assertEquals(ISSUER, issuer);
          assertEquals(KID, kid);
          assertEquals(algorithm.jose, alg);
          assertEquals(JwtType.ACCESS, type);
          return publicKey;
        });
  }

  private static JwtValidator validator(Algorithm algorithm, JwtVerificationKeyResolver resolver) {
    JwsVerifier unexpectedVerifier =
        (input, signature, key) -> {
          throw new AssertionError("Tried the wrong verifier: " + algorithm.other().jose);
        };
    return new JwtValidator(
        Map.of(algorithm.jose, algorithm.verifier(), algorithm.other().jose, unexpectedVerifier),
        CLOCK,
        resolver,
        false);
  }

  private static JwtVerificationKeyResolver trustedKey(
      PublicKey key, Algorithm algorithm, JwtType type) {
    return (issuer, kid, requestedAlgorithm, requestedType) -> {
      if (!ISSUER.equals(issuer)
          || !KID.equals(kid)
          || !algorithm.jose.equals(requestedAlgorithm)
          || requestedType != type) {
        throw new JwtVerificationException("No matching supplied key");
      }
      return key;
    };
  }

  private static JwtValidator beforeKeyLookup() {
    return new JwtValidator(
        Map.of("RS256", new Rs256JwsVerifier(), "ML-DSA-65", new MLDSA65JwsVerifier()),
        CLOCK,
        (issuer, kid, algorithm, type) -> {
          throw new AssertionError("Invalid token reached key resolution");
        });
  }

  private static void assertAuthenticated(JwtValidator validator, String token) {
    assertEquals("fixture-user", validator.validate(token, POLICY).claims().path("sub").asText());
  }

  private static void assertRejected(JwtValidator validator, String token) {
    assertThrows(JwtVerificationException.class, () -> validator.validate(token, POLICY));
  }

  private static String header(Algorithm algorithm) {
    return String.format(
        "{ \"typ\": \"at+jwt\", \"kid\": \"%s\", \"alg\": \"%s\" }", KID, algorithm.jose);
  }

  private static String sign(Algorithm algorithm, String header, String payload)
      throws GeneralSecurityException {
    return JwtTestTokens.signed(header, payload, algorithm.signatureAlgorithm, algorithm.keys());
  }

  private static String signEncoded(Algorithm algorithm, String header, String payload)
      throws GeneralSecurityException {
    String input = header + "." + payload;
    Signature signer = Signature.getInstance(algorithm.signatureAlgorithm);
    signer.initSign(algorithm.keys().getPrivate());
    signer.update(input.getBytes(US_ASCII));
    return input + "." + ENCODER.encodeToString(signer.sign());
  }

  private static String invalidUtf8(String json) {
    byte[] bytes = addMember(json.strip(), "\"ext\":\"@\"").getBytes(UTF_8);
    bytes[bytes.length - 3] = (byte) 0xC0;
    return ENCODER.encodeToString(bytes);
  }

  private static String addMember(String json, String member) {
    return json.substring(0, json.length() - 1) + "," + member + "}";
  }

  private static String nestedMember(int arrays) {
    return "\"ext\":" + "[".repeat(arrays) + "0" + "]".repeat(arrays);
  }

  private static String padTo(String json, int bytes) {
    return json + " ".repeat(bytes - json.length());
  }

  private static String encode(String value) {
    return ENCODER.encodeToString(value.getBytes(UTF_8));
  }

  private static String replacePart(String[] parts, int index, String part) {
    String[] copy = parts.clone();
    copy[index] = part;
    return String.join(".", copy);
  }

  private static byte[] resource(String name) throws IOException {
    try (var stream =
        Objects.requireNonNull(
            JwtStrictJwsTest.class.getResourceAsStream("/ml-dsa-65-openssl/" + name), name)) {
      return stream.readAllBytes();
    }
  }

  private static String publicJwk(Algorithm algorithm) {
    PublicKey key = algorithm.keys().getPublic();
    if (key instanceof RSAPublicKey rsa) {
      byte[] modulus = rsa.getModulus().toByteArray();
      if (modulus[0] == 0) {
        modulus = Arrays.copyOfRange(modulus, 1, modulus.length);
      }
      return String.format(
          "{\"kty\":\"RSA\",\"n\":\"%s\",\"e\":\"%s\"}",
          ENCODER.encodeToString(modulus),
          ENCODER.encodeToString(rsa.getPublicExponent().toByteArray()));
    }
    byte[] spki = key.getEncoded();
    byte[] pub = Arrays.copyOfRange(spki, spki.length - 1952, spki.length);
    return String.format(
        "{\"kty\":\"AKP\",\"alg\":\"ML-DSA-65\",\"pub\":\"%s\"}", ENCODER.encodeToString(pub));
  }

  private enum Algorithm {
    RS256("RS256", "RSA", "SHA256withRSA"),
    ML_DSA_65("ML-DSA-65", "ML-DSA-65", "ML-DSA-65");

    private final String jose;
    private final String keyAlgorithm;
    private final String signatureAlgorithm;

    Algorithm(String jose, String keyAlgorithm, String signatureAlgorithm) {
      this.jose = jose;
      this.keyAlgorithm = keyAlgorithm;
      this.signatureAlgorithm = signatureAlgorithm;
    }

    private KeyPair keys() {
      return this == RS256 ? JwtTestTokens.RSA_KEYS : JwtTestTokens.ML_DSA_KEYS;
    }

    private Algorithm other() {
      return this == RS256 ? ML_DSA_65 : RS256;
    }

    private JwsVerifier verifier() {
      return this == RS256 ? new Rs256JwsVerifier() : new MLDSA65JwsVerifier();
    }
  }
}
