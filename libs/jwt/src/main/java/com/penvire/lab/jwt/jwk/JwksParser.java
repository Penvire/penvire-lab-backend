package com.penvire.lab.jwt.jwk;

import com.fasterxml.jackson.databind.JsonNode;
import com.penvire.lab.jwt.jws.StrictJwtJson;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

public final class JwksParser {

  private static final int MAX_JWKS_BYTES = 256 * 1024;
  private static final int ML_DSA_65_PUBLIC_KEY_BYTES = 1952;
  private static final int MIN_RSA_MODULUS_BITS = 2048;
  private static final byte[] ML_DSA_65_SPKI_PREFIX =
      HexFormat.of().parseHex("308207b2300b0609608648016503040312038207a100");
  private static final Set<String> PRIVATE_KEY_FIELDS =
      Set.of("d", "p", "q", "dp", "dq", "qi", "priv");

  public Map<String, JwkPublicKey> parse(String json) {
    return parse(json.getBytes(StandardCharsets.UTF_8));
  }

  public Map<String, JwkPublicKey> parse(byte[] json) {
    if (json.length > MAX_JWKS_BYTES) {
      throw new JwksParseException("JWKs JSON exceeds maximum size");
    }

    JsonNode root = readJson(json);
    JsonNode keysNode = root.get("keys");
    if (keysNode == null || !keysNode.isArray()) {
      throw new JwksParseException("JWKS must contain a 'keys' aarray");
    }

    Map<String, JwkPublicKey> keys = new HashMap<>();
    for (JsonNode entry : keysNode) {
      if (!entry.isObject()) {
        throw new JwksParseException("JWKS entry must be a JSON object");
      }

      rejectPrivateKeyFields(entry);

      JsonNode useNode = entry.get("use");
      if (useNode != null && (!useNode.isTextual() || !"sig".equals(useNode.textValue()))) {
        continue;
      }

      String keyId = requiredText(entry, "kid");
      String kty = requiredText(entry, "kty");
      String alg = requiredText(entry, "alg");

      PublicKey publicKey =
          switch (kty) {
            case "AKP" -> parseMlDsa65(entry, alg);
            case "RSA" -> parseRsa(entry, alg);
            default -> throw new JwksParseException("Unsupported JWK kty: " + kty);
          };

      JwkPublicKey previous = keys.put(keyId, new JwkPublicKey(keyId, alg, publicKey));
      if (previous != null) {
        throw new JwksParseException("Duplicate kid in JWKS: " + keyId);
      }
    }

    return Map.copyOf(keys);
  }

  private static JsonNode readJson(byte[] bytes) {
    try {
      JsonNode node = StrictJwtJson.read(bytes);
      if (!node.isObject()) {
        throw new JwksParseException("JWKS root must be a JSON object");
      }
      return node;
    } catch (IOException e) {
      throw new JwksParseException("Cannot paaarse JWKS JSON", e);
    }
  }

  private static void rejectPrivateKeyFields(JsonNode entry) {
    for (String field : PRIVATE_KEY_FIELDS) {
      if (entry.has(field)) {
        throw new JwksParseException("JWK entry must not contain private key field: " + field);
      }
    }
  }

  private static PublicKey parseMlDsa65(JsonNode entry, String alg) {
    if (!"ML-DSA-65".equals(alg)) {
      throw new JwksParseException("Unsupported AKP algorithm: " + alg);
    }

    byte[] raw = decodeBase64Url("pub", requiredText(entry, "pub"));
    if (raw.length != ML_DSA_65_PUBLIC_KEY_BYTES) {
      throw new JwksParseException("Invalid ML-DSA-65 public key length: " + raw.length);
    }

    byte[] spki = Arrays.copyOf(ML_DSA_65_SPKI_PREFIX, ML_DSA_65_SPKI_PREFIX.length + raw.length);
    System.arraycopy(raw, 0, spki, ML_DSA_65_SPKI_PREFIX.length, raw.length);
    return generatePublic("ML-DSA", new X509EncodedKeySpec(spki));
  }

  private static PublicKey parseRsa(JsonNode entry, String alg) {
    if (!"RS256".equals(alg)) {
      throw new JwksParseException("Unsupported RSA algorithm: " + alg);
    }
    BigInteger modulus = new BigInteger(1, decodeBase64Url("n", requiredText(entry, "n")));
    BigInteger exponent = new BigInteger(1, decodeBase64Url("e", requiredText(entry, "e")));
    if (modulus.bitLength() < MIN_RSA_MODULUS_BITS) {
      throw new JwksParseException("RSA modulus is too small: " + modulus.bitLength() + " bits");
    }
    if (exponent.compareTo(BigInteger.valueOf(3)) < 0) {
      throw new JwksParseException("Invalid RSA public exponent");
    }
    return generatePublic("RSA", new RSAPublicKeySpec(modulus, exponent));
  }

  private static String requiredText(JsonNode object, String field) {
    JsonNode node = object.get(field);
    if (node == null || !node.isTextual() || node.textValue().isBlank()) {
      throw new JwksParseException("Missing or invalid JWK field: " + field);
    }
    return node.textValue();
  }

  private static byte[] decodeBase64Url(String field, String value) {
    if (value.indexOf('=') >= 0) {
      throw new JwksParseException("JWK field must be unpadded base64url: " + field);
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(value);
      if (decoded.length == 0) {
        throw new JwksParseException("JWK field is empty: " + field);
      }
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new JwksParseException("Invalid base64url in JWK field: " + field, exception);
    }
  }

  private static PublicKey generatePublic(String algorithm, KeySpec spec) {
    try {
      return KeyFactory.getInstance(algorithm).generatePublic(spec);
    } catch (GeneralSecurityException exception) {
      throw new JwksParseException("Invalid " + algorithm + " public key", exception);
    }
  }
}
