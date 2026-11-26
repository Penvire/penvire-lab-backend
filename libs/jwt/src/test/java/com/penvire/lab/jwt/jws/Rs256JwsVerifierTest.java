package com.penvire.lab.jwt.jws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.jwt.validation.JwtTestTokens;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Rs256JwsVerifierTest {

  private final Rs256JwsVerifier verifier = new Rs256JwsVerifier();

  @Test
  void verifiesTheIndependentRfc7520Signature() throws Exception {
    String[] parts = JwtTestTokens.fixtureText("compact.jws").split("\\.", -1);

    assertTrue(
        verifier.verifySignature(
            signingInput(parts), signature(parts), JwtTestTokens.fixturePublicKey()));
  }

  @Test
  void rejectsModifiedSigningInput() throws Exception {
    String[] parts = JwtTestTokens.fixtureText("compact.jws").split("\\.", -1);
    byte[] input = signingInput(parts);
    input[0] ^= 1;

    assertFalse(
        verifier.verifySignature(input, signature(parts), JwtTestTokens.fixturePublicKey()));
  }

  @Test
  void rejectsModifiedSignature() throws Exception {
    String[] parts = JwtTestTokens.fixtureText("compact.jws").split("\\.", -1);
    byte[] signature = signature(parts);
    signature[signature.length - 1] ^= 1;

    assertFalse(
        verifier.verifySignature(signingInput(parts), signature, JwtTestTokens.fixturePublicKey()));
  }

  @Test
  void rejectsTheWrongRsaKey() throws Exception {
    String[] parts = JwtTestTokens.fixtureText("compact.jws").split("\\.", -1);

    assertFalse(
        verifier.verifySignature(
            signingInput(parts), signature(parts), JwtTestTokens.RSA_KEYS.getPublic()));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 255, 257})
  void rejectsWrongSignatureLengths(int length) {
    assertFalse(
        verifier.verifySignature(
            new byte[] {1}, new byte[length], JwtTestTokens.RSA_KEYS.getPublic()));
  }

  @Test
  void rejectsNonRsaKeys() {
    assertThrows(
        JwsVerificationException.class,
        () ->
            verifier.verifySignature(
                new byte[] {1}, new byte[256], JwtTestTokens.ML_DSA_KEYS.getPublic()));
  }

  @Test
  void rejectsUndersizedRsaKeys() {
    var key = new StubRsaKey(BigInteger.ONE.shiftLeft(2046).add(BigInteger.ONE));

    var failure =
        assertThrows(
            JwsVerificationException.class,
            () -> verifier.verifySignature(new byte[] {1}, new byte[256], key));

    assertEquals("RSA keys must have at least 2048 bits", failure.getMessage());
  }

  @Test
  void translatesAnUnusableRsaKeyIntoAVerificationFailure() {
    var key = new StubRsaKey(BigInteger.ONE.shiftLeft(2047).add(BigInteger.ONE));
    var failure =
        assertThrows(
            JwsVerificationException.class,
            () -> verifier.verifySignature(new byte[] {1}, new byte[256], key));

    assertInstanceOf(GeneralSecurityException.class, failure.getCause());
  }

  private static byte[] signingInput(String[] parts) {
    return (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] signature(String[] parts) {
    return Base64.getUrlDecoder().decode(parts[2]);
  }

  private record StubRsaKey(BigInteger modulus) implements RSAPublicKey {
    @Override
    public BigInteger getModulus() {
      return modulus;
    }

    @Override
    public BigInteger getPublicExponent() {
      return BigInteger.ZERO;
    }

    @Override
    public String getAlgorithm() {
      return "RSA";
    }

    @Override
    public String getFormat() {
      return "X.509";
    }

    @Override
    public byte[] getEncoded() {
      return new byte[0];
    }
  }
}
