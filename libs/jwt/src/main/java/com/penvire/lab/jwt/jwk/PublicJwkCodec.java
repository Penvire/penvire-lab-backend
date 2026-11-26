package com.penvire.lab.jwt.jwk;

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
import org.jspecify.annotations.Nullable;

/**
 * Encodes and decodes public JWK key material for RFC 9964 {@code AKP} ({@code ML-DSA-65}) and
 * {@code RSA} ({@code RS256}) keys. Private material is never handled here.
 */
public final class PublicJwkCodec {

  public static final String KTY_AKP = "AKP";
  public static final String KTY_RSA = "RSA";
  public static final String ALG_ML_DSA_65 = "ML-DSA-65";
  public static final String ALG_RS256 = "RS256";

  /** Raw ML-DSA-65 public key length (FIPS 204). */
  public static final int ML_DSA_65_PUBLIC_KEY_BYTES = 1952;

  private static final int MIN_RSA_MODULUS_BITS = 2048;

  /** The X.509 SubjectPublicKeyInfo header Java puts in front of a raw ML-DSA-65 public key. */
  private static final byte[] ML_DSA_65_SPKI_PREFIX =
      HexFormat.of().parseHex("308207b2300b0609608648016503040312038207a100");

  private PublicJwkCodec() {}

  public record RsaParameters(String modulus, String exponent) {}

  public static String encodeMlDsa65(PublicKey publicKey) {
    byte[] encoded = publicKey.getEncoded();
    byte[] prefix = Arrays.copyOf(encoded, ML_DSA_65_SPKI_PREFIX.length);
    if (encoded.length != ML_DSA_65_SPKI_PREFIX.length + ML_DSA_65_PUBLIC_KEY_BYTES
        || !Arrays.equals(prefix, ML_DSA_65_SPKI_PREFIX)) {
      throw new IllegalStateException("Not an X.509-encoded ML-DSA-65 public key");
    }
    byte[] raw = Arrays.copyOfRange(encoded, ML_DSA_65_SPKI_PREFIX.length, encoded.length);
    return base64Url(raw);
  }

  public static PublicKey decodeMlDsa65(@Nullable String pubBase64Url) {
    byte[] raw = decodeBase64Url("pub", pubBase64Url);
    if (raw.length != ML_DSA_65_PUBLIC_KEY_BYTES) {
      throw new IllegalArgumentException(
          "AKP pub must be " + ML_DSA_65_PUBLIC_KEY_BYTES + " bytes, was " + raw.length);
    }
    return generatePublic("ML-DSA", new X509EncodedKeySpec(concat(ML_DSA_65_SPKI_PREFIX, raw)));
  }

  public static RsaParameters encodeRsa(RSAPublicKey publicKey) {
    return new RsaParameters(
        base64Url(unsigned(publicKey.getModulus())),
        base64Url(unsigned(publicKey.getPublicExponent())));
  }

  public static PublicKey decodeRsa(
      @Nullable String modulusBase64Url, @Nullable String exponentBase64Url) {
    BigInteger modulus = new BigInteger(1, decodeBase64Url("n", modulusBase64Url));
    BigInteger exponent = new BigInteger(1, decodeBase64Url("e", exponentBase64Url));
    if (modulus.bitLength() < MIN_RSA_MODULUS_BITS) {
      throw new IllegalArgumentException(
          "RSA modulus must be at least "
              + MIN_RSA_MODULUS_BITS
              + " bits, was "
              + modulus.bitLength());
    }
    if (exponent.compareTo(BigInteger.valueOf(3)) < 0) {
      throw new IllegalArgumentException("Invalid RSA public exponent");
    }
    return generatePublic("RSA", new RSAPublicKeySpec(modulus, exponent));
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
      byte[] decoded = Base64.getUrlDecoder().decode(value);
      if (decoded.length == 0) {
        throw new IllegalArgumentException("JWK member " + member + " is empty");
      }
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("JWK member " + member + " is not base64url", exception);
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
    } catch (GeneralSecurityException exception) {
      throw new IllegalArgumentException(
          "JWK is not a valid " + algorithm + " public key", exception);
    }
  }
}
