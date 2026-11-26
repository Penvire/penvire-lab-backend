package com.penvire.lab.jwt.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.penvire.lab.jwt.jws.JwsVerificationException;
import com.penvire.lab.jwt.jws.JwsVerifier;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class JwtTestTokens {

  static final String ISSUER = "https://identity.example.test";
  static final String AUDIENCE = "tenant-api";
  static final String KEY_ID = "test-signing-key";
  static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  public static final KeyPair RSA_KEYS = generateKeys("RSA", 2048);
  public static final KeyPair ML_DSA_KEYS = generateKeys("ML-DSA-65", 0);
  static final ObjectMapper JSON = new ObjectMapper();

  private JwtTestTokens() {}

  public static ObjectNode header(JwtType type, String algorithm) {
    return JSON.createObjectNode()
        .put("alg", algorithm)
        .put("typ", type.headerValue())
        .put("kid", KEY_ID);
  }

  static ObjectNode accessClaims() {
    return JSON.createObjectNode()
        .put("iss", ISSUER)
        .put("sub", "external-user-subject")
        .put("aud", AUDIENCE)
        .put("jti", "access-token-id")
        .put("client_id", "lab-bff")
        .put("iat", NOW.minusSeconds(60).getEpochSecond())
        .put("nbf", NOW.minusSeconds(60).getEpochSecond())
        .put("exp", NOW.plusSeconds(240).getEpochSecond());
  }

  static ObjectNode refreshClaims() {
    return accessClaims()
        .put("aud", "identity")
        .put("jti", "refresh-token-id")
        .put("sid", "session-id")
        .put("family_id", "refresh-family-id");
  }

  static JwtValidationPolicy accessPolicy() {
    return policy(Set.of(AUDIENCE), Set.of(JwtType.ACCESS), Set.of("RS256"));
  }

  static JwtValidationPolicy refreshPolicy(String algorithm) {
    return policy(Set.of("identity"), Set.of(JwtType.REFRESH), Set.of(algorithm));
  }

  static JwtValidationPolicy policy(
      Set<String> audiences, Set<JwtType> types, Set<String> algorithms) {
    return new JwtValidationPolicy(ISSUER, audiences, types, algorithms, Duration.ofSeconds(30));
  }

  static String rsaToken(ObjectNode header, JsonNode claims) throws GeneralSecurityException {
    return signed(header.toString(), claims.toString(), "SHA256withRSA", RSA_KEYS);
  }

  static String accessToken(ObjectNode claims) throws GeneralSecurityException {
    return rsaToken(header(JwtType.ACCESS, "RS256"), claims);
  }

  static String refreshToken(ObjectNode claims) throws GeneralSecurityException {
    return signed(
        header(JwtType.REFRESH, "ML-DSA-65").toString(),
        claims.toString(),
        "ML-DSA-65",
        ML_DSA_KEYS);
  }

  static String signed(String header, String payload, String jcaAlgorithm, KeyPair keys)
      throws GeneralSecurityException {
    String input = encode(header) + "." + encode(payload);
    Signature signer = Signature.getInstance(jcaAlgorithm);
    signer.initSign(keys.getPrivate());
    signer.update(input.getBytes(StandardCharsets.US_ASCII));
    return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
  }

  static JwsVerifier mlDsaSignatureVerifier() {
    return (input, signature, key) -> {
      try {
        Signature verifier = Signature.getInstance("ML-DSA-65");
        verifier.initVerify(key);
        verifier.update(input);
        return verifier.verify(signature);
      } catch (GeneralSecurityException exception) {
        throw new JwsVerificationException("ML-DSA test signature verification failed", exception);
      }
    };
  }

  public static String encode(String text) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  static JsonNode json(String text) throws IOException {
    return Objects.requireNonNull(JSON.readTree(text));
  }

  public static String fixtureText(String name) throws IOException {
    try (InputStream input = fixtureStream(name)) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
    }
  }

  public static PublicKey fixturePublicKey() throws IOException, GeneralSecurityException {
    var properties = new Properties();
    try (InputStream input = fixtureStream("public-key.properties")) {
      properties.load(input);
    }
    var decoder = Base64.getUrlDecoder();
    var modulus = new BigInteger(1, decoder.decode(properties.getProperty("n")));
    var exponent = new BigInteger(1, decoder.decode(properties.getProperty("e")));
    return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
  }

  private static InputStream fixtureStream(String name) {
    return Objects.requireNonNull(
        JwtTestTokens.class.getResourceAsStream("/rs256-rfc7520/" + name),
        "Missing RFC 7520 fixture: " + name);
  }

  private static KeyPair generateKeys(String algorithm, int bits) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
      if (bits != 0) {
        generator.initialize(bits);
      }
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
