package com.penvire.lab.jwt.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.penvire.lab.jwt.jws.JwsDecoder;
import com.penvire.lab.jwt.jws.JwsVerificationException;
import com.penvire.lab.jwt.jws.JwsVerifier;
import com.penvire.lab.jwt.jws.ParsedJws;
import com.penvire.lab.jwt.jws.StrictJwtJson;
import java.io.IOException;
import java.security.PublicKey;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class JwtValidator {
  private static final int MAX_COMPACT_JWS_CHARS = 32 * 1024;

  private final JwsDecoder decoder = new JwsDecoder();
  private final Map<String, JwsVerifier> signatureVerifiers;
  private final Clock clock;

  private final JwtVerificationKeyResolver keyResolver;

  private final boolean useEnforcedPQCRefreshTokens; // Default true

  public JwtValidator(
      Map<String, JwsVerifier> signatureVerifiers,
      Clock clock,
      JwtVerificationKeyResolver keyResolver) {
    this(signatureVerifiers, clock, keyResolver, true);
  }

  public JwtValidator(
      Map<String, JwsVerifier> signatureVerifiers,
      Clock clock,
      JwtVerificationKeyResolver keyResolver,
      boolean useEnforcedPQCRefreshTokens) {
    this.signatureVerifiers = Map.copyOf(signatureVerifiers);
    this.clock = Objects.requireNonNull(clock, "clock");
    this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
    this.useEnforcedPQCRefreshTokens = useEnforcedPQCRefreshTokens;
  }

  public VerifiedJwt validate(String compactJwt, JwtValidationPolicy policy) {
    ParsedJws parsedJws = decode(compactJwt);
    JsonNode header = parsedJws.header();

    String algorithm = requiredText(header, "alg");
    if (!policy.allowedAlgorithms().contains(algorithm)) {
      throw new JwtVerificationException("Algorithm not allowed");
    }

    JwsVerifier verifier = signatureVerifiers.get(algorithm);
    if (verifier == null) {
      throw new JwtVerificationException("JWT algorithm is not configured");
    }

    JwtType type = validateHeader(header, policy, algorithm);
    String keyId = requiredText(header, "kid");

    PublicKey publicKey = keyResolver.resolve(policy.issuer(), keyId, algorithm, type);

    verifySignature(parsedJws, verifier, publicKey);

    JsonNode claims = readClaims(parsedJws.payload());
    validateClaims(claims, policy, type, clock.instant());

    return new VerifiedJwt(claims);
  }

  private ParsedJws decode(String compactJwt) {
    if (compactJwt.length() > MAX_COMPACT_JWS_CHARS) {
      throw new JwtVerificationException("JWT is too large");
    }

    try {
      return decoder.decode(compactJwt);
    } catch (JwsVerificationException exception) {
      throw new JwtVerificationException("Cannot decode JWT", exception);
    }
  }

  private JwtType validateHeader(JsonNode header, JwtValidationPolicy policy, String algorithm) {
    String typeHeader = requiredText(header, "typ");

    JwtType type =
        policy.types().stream()
            .filter(candidate -> candidate.headerValue().equals(typeHeader))
            .findFirst()
            .orElseThrow(() -> new JwtVerificationException("JWT type is not accepted"));

    if (useEnforcedPQCRefreshTokens
        && type == JwtType.REFRESH
        && !"ML-DSA-65".equalsIgnoreCase(algorithm)) {
      throw new JwtVerificationException("JWT type is not accepted");
    }

    if (header.has("crit")) {
      throw new JwtVerificationException("Critical JWT extension not supported");
    }

    JsonNode encoding = header.path("b64");
    if (header.has("b64") && (!encoding.isBoolean() || !encoding.booleanValue())) {
      throw new JwtVerificationException("Unsupported JWT payload encoding");
    }

    return type;
  }

  private void validateClaims(
      JsonNode claims, JwtValidationPolicy policy, JwtType type, Instant now) {
    if (!policy.issuer().equals(requiredText(claims, "iss"))) {
      throw new JwtVerificationException("JWT issuer is not accepted");
    }

    requiredText(claims, "sub");
    requiredText(claims, "jti");
    requiredText(claims, "client_id");

    validateAudience(claims.path("aud"), policy);
    validateTimeClaims(claims, policy.clockSkew(), now);

    switch (type) {
      case ACCESS -> {
        // Access tokens can represent users or autonomous services.
      }
      case REFRESH -> {
        requiredText(claims, "sid");
        requiredText(claims, "family_id");
      }
    }
  }

  private void validateAudience(JsonNode audience, JwtValidationPolicy policy) {
    boolean matches;

    if (audience.isTextual()) {
      String value = audience.textValue();

      if (value.isBlank()) {
        throw new JwtVerificationException("Invalid JWT audience");
      }

      matches = policy.audiences().contains(value);
    } else if (audience.isArray() && !audience.isEmpty()) {
      matches = false;

      for (JsonNode entry : audience) {
        if (!entry.isTextual() || entry.textValue().isBlank()) {
          throw new JwtVerificationException("Invalid JWT audience");
        }

        if (policy.audiences().contains(entry.textValue())) {
          matches = true;
        }
      }
    } else {
      throw new JwtVerificationException("Missing or invalid JWT audience");
    }

    if (!matches) {
      throw new JwtVerificationException("JWT audience is not accepted");
    }
  }

  private void validateTimeClaims(JsonNode claims, Duration skew, Instant now) {
    Instant expiresAt = requiredNumericDate(claims, "exp");
    Instant notBefore = requiredNumericDate(claims, "nbf");
    Instant issuedAt = requiredNumericDate(claims, "iat");

    if (!expiresAt.isAfter(issuedAt)) {
      throw new JwtVerificationException("JWT expiry must follow issuance");
    }

    if (Duration.between(expiresAt, now).compareTo(skew) >= 0) {
      throw new JwtVerificationException("JWT has expired");
    }

    if (Duration.between(now, issuedAt).compareTo(skew) > 0) {
      throw new JwtVerificationException("JWT was issued in the future");
    }

    if (!notBefore.isBefore(expiresAt)) {
      throw new JwtVerificationException("JWT validity starts after its usable lifetime");
    }

    if (Duration.between(now, notBefore).compareTo(skew) > 0) {
      throw new JwtVerificationException("JWT is not yet valid");
    }
  }

  private void verifySignature(ParsedJws parsed, JwsVerifier verifier, PublicKey publicKey) {
    try {
      boolean valid =
          verifier.verifySignature(parsed.signingInput(), parsed.signature(), publicKey);

      if (!valid) {
        throw new JwtVerificationException("Invalid JWT signature");
      }
    } catch (JwsVerificationException exception) {
      throw new JwtVerificationException("Cannot verify JWT signature", exception);
    }
  }

  private JsonNode readClaims(byte[] payload) {
    try {
      JsonNode claims = StrictJwtJson.read(payload);

      if (claims == null || !claims.isObject()) {
        throw new JwtVerificationException("JWT payload must be a JSON object");
      }

      return claims;
    } catch (IOException exception) {
      throw new JwtVerificationException("Cannot parse JWT claims", exception);
    }
  }

  private String requiredText(JsonNode object, String name) {
    JsonNode node = object.get(name);

    if (node == null || !node.isTextual() || node.textValue().isBlank()) {
      throw new JwtVerificationException("Missing or invalid JWT field: " + name);
    }

    return node.textValue();
  }

  private Instant requiredNumericDate(JsonNode object, String name) {
    JsonNode node = object.get(name);

    if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
      throw new JwtVerificationException("Missing or invalid JWT field: " + name);
    }

    try {
      return Instant.ofEpochSecond(node.longValue());
    } catch (DateTimeException exception) {
      throw new JwtVerificationException("Invalid JWT timestamp: " + name, exception);
    }
  }
}
