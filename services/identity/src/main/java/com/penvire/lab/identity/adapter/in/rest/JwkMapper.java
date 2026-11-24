package com.penvire.lab.identity.adapter.in.rest;

import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.server.model.Jwk;
import com.penvire.lab.identity.server.model.JwkSet;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

/**
 * Maps signing keys to public JWKs and back. ML-DSA-65 keys are RFC 9964 AKP keys with the raw
 * public key in {@code pub}; RS256 keys are RSA keys with {@code n} and {@code e} as unsigned
 * big-endian integers. Private material is never part of a JWK.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public abstract class JwkMapper {

  /** Raw ML-DSA-65 public key length (FIPS 204). */
  private static final int ML_DSA_65_PUBLIC_KEY_BYTES = 1952;

  /** The X.509 SubjectPublicKeyInfo header Java puts in front of a raw ML-DSA-65 public key. */
  private static final byte[] ML_DSA_65_SPKI_PREFIX =
      HexFormat.of().parseHex("308207b2300b0609608648016503040312038207a100");

  private static final String KTY_AKP = "AKP";
  private static final String KTY_RSA = "RSA";

  /** A key's {@code kid} and {@code alg} come from the stored key, never from the key bytes. */
  @Mapping(target = "kid", expression = "java(key.kid())")
  @Mapping(target = "alg", source = "algorithm")
  @Mapping(target = "use", constant = "sig")
  @Mapping(target = "kty", ignore = true)
  @Mapping(target = "pub", ignore = true)
  @Mapping(target = "n", ignore = true)
  @Mapping(target = "e", ignore = true)
  public abstract Jwk toJwk(SigningKey key);

  public abstract List<Jwk> toJwks(List<SigningKey> keys);

  // MapStruct cannot map a list straight onto a bean, so the wrapper is built by hand.
  public JwkSet toJwkSet(List<SigningKey> keys) {
    return new JwkSet(toJwks(keys));
  }

  /** The JOSE name, not the enum constant: {@code ML-DSA-65}, not {@code ML_DSA_65}. */
  protected String toAlg(SigningKeyAlgorithm algorithm) {
    return algorithm.joseName();
  }

  @AfterMapping
  protected void addKeyMaterial(SigningKey key, @MappingTarget Jwk jwk) {
    switch (key.algorithm()) {
      case ML_DSA_65 -> {
        jwk.setKty(KTY_AKP);
        jwk.setPub(base64Url(rawMlDsa65(key.publicKey())));
      }
      case RS256 -> {
        var rsa = (RSAPublicKey) key.publicKey();
        jwk.setKty(KTY_RSA);
        jwk.setN(base64Url(unsigned(rsa.getModulus())));
        jwk.setE(base64Url(unsigned(rsa.getPublicExponent())));
      }
    }
  }

  /**
   * Reads a public JWK. Only the two key types Identity uses are accepted, each with its one
   * algorithm; anything else is rejected rather than guessed. Parsing a key does not make it
   * trusted.
   *
   * @throws IllegalArgumentException if the JWK is malformed or its metadata does not match
   */
  public PublicKey toPublicKey(Jwk jwk) {
    return switch (jwk.getKty()) {
      case KTY_AKP -> {
        requireAlg(jwk, SigningKeyAlgorithm.ML_DSA_65);
        byte[] raw = decodeBase64Url("pub", jwk.getPub());
        if (raw.length != ML_DSA_65_PUBLIC_KEY_BYTES) {
          throw new IllegalArgumentException(
              "AKP pub must be " + ML_DSA_65_PUBLIC_KEY_BYTES + " bytes, was " + raw.length);
        }
        yield generatePublic("ML-DSA", new X509EncodedKeySpec(concat(ML_DSA_65_SPKI_PREFIX, raw)));
      }
      case KTY_RSA -> {
        requireAlg(jwk, SigningKeyAlgorithm.RS256);
        var modulus = new BigInteger(1, decodeBase64Url("n", jwk.getN()));
        var exponent = new BigInteger(1, decodeBase64Url("e", jwk.getE()));
        yield generatePublic("RSA", new RSAPublicKeySpec(modulus, exponent));
      }
      default -> throw new IllegalArgumentException("Unsupported JWK kty: " + jwk.getKty());
    };
  }

  private static void requireAlg(Jwk jwk, SigningKeyAlgorithm expected) {
    if (!expected.joseName().equals(jwk.getAlg())) {
      throw new IllegalArgumentException(
          "JWK kty "
              + jwk.getKty()
              + " requires alg "
              + expected.joseName()
              + ", was "
              + jwk.getAlg());
    }
  }

  private static byte[] rawMlDsa65(PublicKey publicKey) {
    byte[] encoded = publicKey.getEncoded();
    byte[] prefix = Arrays.copyOf(encoded, ML_DSA_65_SPKI_PREFIX.length);
    if (encoded.length != ML_DSA_65_SPKI_PREFIX.length + ML_DSA_65_PUBLIC_KEY_BYTES
        || !Arrays.equals(prefix, ML_DSA_65_SPKI_PREFIX)) {
      throw new IllegalStateException("Not an X.509-encoded ML-DSA-65 public key");
    }
    return Arrays.copyOfRange(encoded, ML_DSA_65_SPKI_PREFIX.length, encoded.length);
  }

  /** Big-endian magnitude without the sign byte {@link BigInteger#toByteArray} may add. */
  private static byte[] unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    return bytes.length > 1 && bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
  }

  private static String base64Url(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static byte[] decodeBase64Url(String member, @Nullable String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("JWK member " + member + " is missing");
    }
    if (value.indexOf('=') >= 0) {
      throw new IllegalArgumentException("JWK member " + member + " must be unpadded base64url");
    }
    try {
      return Base64.getUrlDecoder().decode(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("JWK member " + member + " is not base64url", e);
    }
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private static PublicKey generatePublic(String algorithm, KeySpec spec) {
    try {
      return KeyFactory.getInstance(algorithm).generatePublic(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("JWK is not a valid " + algorithm + " public key", e);
    }
  }
}
