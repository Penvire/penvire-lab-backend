package com.penvire.lab.jwt.jwk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class JwksParserTest {

  private final JwksParser parser = new JwksParser();

  private static PublicKey mlDsa65PublicKey() throws GeneralSecurityException {
    var generator = KeyPairGenerator.getInstance("ML-DSA");
    generator.initialize(NamedParameterSpec.ML_DSA_65);
    return generator.generateKeyPair().getPublic();
  }

  private static RSAPublicKey rsaPublicKey(int bits) throws GeneralSecurityException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    return (RSAPublicKey) generator.generateKeyPair().getPublic();
  }

  @Test
  void parsesMlDsa65AndRs256KeysFromJwks() throws GeneralSecurityException {
    PublicKey mlDsaKey = mlDsa65PublicKey();
    RSAPublicKey rsaKey = rsaPublicKey(2048);

    String pub = PublicJwkCodec.encodeMlDsa65(mlDsaKey);
    PublicJwkCodec.RsaParameters rsa = PublicJwkCodec.encodeRsa(rsaKey);

    String jwks =
        """
        {
          "keys": [
            {"kty": "AKP", "alg": "ML-DSA-65", "use": "sig", "kid": "mldsa-1", "pub": "%s"},
            {"kty": "RSA", "alg": "RS256", "use": "sig", "kid": "rsa-1", "n": "%s", "e": "%s"}
          ]
        }
        """
            .formatted(pub, rsa.modulus(), rsa.exponent());

    Map<String, JwkPublicKey> parsed = parser.parse(jwks);
    JwkPublicKey mlDsaParsed = Objects.requireNonNull(parsed.get("mldsa-1"));
    JwkPublicKey rsaParsed = Objects.requireNonNull(parsed.get("rsa-1"));

    assertEquals(2, parsed.size());
    assertEquals("ML-DSA-65", mlDsaParsed.algorithm());
    assertArrayEquals(mlDsaKey.getEncoded(), mlDsaParsed.publicKey().getEncoded());
    assertEquals("RS256", rsaParsed.algorithm());
    assertArrayEquals(rsaKey.getEncoded(), rsaParsed.publicKey().getEncoded());
  }

  @Test
  void skipsNonSignatureKeys() throws GeneralSecurityException {
    RSAPublicKey rsaKey = rsaPublicKey(2048);
    PublicJwkCodec.RsaParameters rsa = PublicJwkCodec.encodeRsa(rsaKey);

    String jwks =
        """
        {
          "keys": [
            {"kty": "RSA", "alg": "RS256", "use": "enc", "kid": "enc-1", "n": "%s", "e": "%s"}
          ]
        }
        """
            .formatted(rsa.modulus(), rsa.exponent());

    assertTrue(parser.parse(jwks).isEmpty());
  }

  @Test
  void rejectsPrivateKeyFields() throws GeneralSecurityException {
    PublicKey mlDsaKey = mlDsa65PublicKey();
    String pub = PublicJwkCodec.encodeMlDsa65(mlDsaKey);

    String jwks =
        """
        {
          "keys": [
            {"kty": "AKP", "alg": "ML-DSA-65", "use": "sig", "kid": "k1", "pub": "%s", "priv": "AA"}
          ]
        }
        """
            .formatted(pub);

    assertThrows(JwksParseException.class, () -> parser.parse(jwks));
  }

  @Test
  void rejectsDuplicateKeyIds() throws GeneralSecurityException {
    PublicKey mlDsaKey = mlDsa65PublicKey();
    String pub = PublicJwkCodec.encodeMlDsa65(mlDsaKey);

    String jwks =
        """
        {
          "keys": [
            {"kty": "AKP", "alg": "ML-DSA-65", "use": "sig", "kid": "dup", "pub": "%s"},
            {"kty": "AKP", "alg": "ML-DSA-65", "use": "sig", "kid": "dup", "pub": "%s"}
          ]
        }
        """
            .formatted(pub, pub);

    assertThrows(JwksParseException.class, () -> parser.parse(jwks));
  }

  @Test
  void rejectsMismatchedAlgorithmForKeyType() throws GeneralSecurityException {
    PublicKey mlDsaKey = mlDsa65PublicKey();
    String pub = PublicJwkCodec.encodeMlDsa65(mlDsaKey);

    String jwks =
        """
        {
          "keys": [
            {"kty": "AKP", "alg": "RS256", "use": "sig", "kid": "k1", "pub": "%s"}
          ]
        }
        """
            .formatted(pub);

    assertThrows(JwksParseException.class, () -> parser.parse(jwks));
  }

  @Test
  void rejectsWrongMlDsaPublicKeyLength() {
    String shortPub = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    String jwks =
        """
        {
          "keys": [
            {"kty": "AKP", "alg": "ML-DSA-65", "use": "sig", "kid": "k1", "pub": "%s"}
          ]
        }
        """
            .formatted(shortPub);

    assertThrows(JwksParseException.class, () -> parser.parse(jwks));
  }

  @Test
  void rejectsRsaModulusSmallerThan2048Bits() throws GeneralSecurityException {
    RSAPublicKey weak = rsaPublicKey(1024);
    PublicJwkCodec.RsaParameters rsa = PublicJwkCodec.encodeRsa(weak);

    String jwks =
        """
        {
          "keys": [
            {"kty": "RSA", "alg": "RS256", "use": "sig", "kid": "weak", "n": "%s", "e": "%s"}
          ]
        }
        """
            .formatted(rsa.modulus(), rsa.exponent());

    assertThrows(JwksParseException.class, () -> parser.parse(jwks));
  }

  @Test
  void rejectsPaddedBase64Url() throws GeneralSecurityException {
    RSAPublicKey rsaKey = rsaPublicKey(2048);
    PublicJwkCodec.RsaParameters rsa = PublicJwkCodec.encodeRsa(rsaKey);
    String paddedE = Base64.getUrlEncoder().encodeToString(new byte[] {1, 0});

    String jwks =
        """
        {
          "keys": [
            {"kty": "RSA", "alg": "RS256", "use": "sig", "kid": "rsa-1", "n": "%s", "e": "%s"}
          ]
        }
        """
            .formatted(rsa.modulus(), paddedE);

    assertThrows(JwksParseException.class, () -> parser.parse(jwks));
  }

  @Test
  void rejectsDuplicateJsonKeysAndMissingKeysArray() {
    assertThrows(JwksParseException.class, () -> parser.parse("{}"));
    assertThrows(JwksParseException.class, () -> parser.parse("{\"keys\":[],\"keys\":[]}"));
  }
}
