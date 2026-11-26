package com.penvire.lab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class TestTokenBuilder {

  static final String ISSUER = "https://id.penvire.test";
  static final String AUDIENCE = "test-api";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private String algorithm = "ML-DSA-65";
  private String type = "at+jwt";
  private String keyId = "key-1";
  private String issuer = ISSUER;
  private String subject = "user-123";
  private String clientId = "client-abc";
  private String tokenId = "jti-001";
  private Object audience = AUDIENCE;
  private @Nullable String scope = null;
  private Instant issuedAt = TestClockProducer.NOW;
  private Instant notBefore = TestClockProducer.NOW;
  private Instant expiresAt = TestClockProducer.NOW.plusSeconds(300);

  static KeyPair generateMlDsa65KeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-DSA");
      generator.initialize(NamedParameterSpec.ML_DSA_65);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Cannot generate ML-DSA-65 key pair", exception);
    }
  }

  static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Cannot generate RSA key pair", exception);
    }
  }

  TestTokenBuilder algorithm(String algorithm) {
    this.algorithm = algorithm;
    return this;
  }

  TestTokenBuilder type(String type) {
    this.type = type;
    return this;
  }

  TestTokenBuilder keyId(String keyId) {
    this.keyId = keyId;
    return this;
  }

  TestTokenBuilder issuer(String issuer) {
    this.issuer = issuer;
    return this;
  }

  TestTokenBuilder subject(String subject) {
    this.subject = subject;
    return this;
  }

  TestTokenBuilder clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  TestTokenBuilder tokenId(String tokenId) {
    this.tokenId = tokenId;
    return this;
  }

  TestTokenBuilder audience(String audience) {
    this.audience = audience;
    return this;
  }

  TestTokenBuilder audiences(List<String> audiences) {
    this.audience = audiences;
    return this;
  }

  TestTokenBuilder scope(@Nullable String scope) {
    this.scope = scope;
    return this;
  }

  TestTokenBuilder issuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
    return this;
  }

  TestTokenBuilder notBefore(Instant notBefore) {
    this.notBefore = notBefore;
    return this;
  }

  TestTokenBuilder expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  String sign(PrivateKey privateKey) {
    try {
      ObjectNode header = JSON.createObjectNode();
      header.put("alg", algorithm);
      header.put("typ", type);
      header.put("kid", keyId);

      ObjectNode claims = JSON.createObjectNode();
      claims.put("iss", issuer);
      claims.put("sub", subject);
      claims.put("client_id", clientId);
      claims.put("jti", tokenId);
      claims.put("iat", issuedAt.getEpochSecond());
      claims.put("nbf", notBefore.getEpochSecond());
      claims.put("exp", expiresAt.getEpochSecond());

      if (audience instanceof String single) {
        claims.put("aud", single);
      } else if (audience instanceof List<?> list) {
        var array = claims.putArray("aud");
        for (Object item : list) {
          array.add(item.toString());
        }
      }

      if (scope != null) {
        claims.put("scope", scope);
      }

      String signingInput =
          BASE64URL.encodeToString(JSON.writeValueAsBytes(header))
              + "."
              + BASE64URL.encodeToString(JSON.writeValueAsBytes(claims));

      Signature signature = Signature.getInstance(jcaAlgorithm(algorithm));
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      byte[] sigBytes = signature.sign();

      return signingInput + "." + BASE64URL.encodeToString(sigBytes);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot sign test JWT", exception);
    }
  }

  private static String jcaAlgorithm(String joseAlgorithm) {
    return switch (joseAlgorithm) {
      case "RS256" -> "SHA256withRSA";
      case "ML-DSA-65" -> "ML-DSA-65";
      default -> throw new IllegalArgumentException("Unsupported algorithm: " + joseAlgorithm);
    };
  }
}
