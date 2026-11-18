package com.penvire.lab.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.jwt.jws.JwsVerificationException;
import com.penvire.lab.jwt.jws.JwsVerifier;
import com.penvire.lab.jwt.jws.Rs256JwsVerifier;
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
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Rs256JwsVerifierTest {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final KeyPair ADVERSARIAL_KEYS = generateRsaKeyPair();

  private final JwsVerifier verifier = new Rs256JwsVerifier();

  @Test
  void verifiesAnIndependentRs256SignatureAndReturnsTheOriginalPayload() throws Exception {
    byte[] payload = verifier.verify(fixtureText("compact.jws").strip(), fixturePublicKey());

    assertArrayEquals(
        fixtureText("payload.txt").stripTrailing().getBytes(StandardCharsets.UTF_8), payload);
  }

  @Test
  void rejectsATamperedProtectedHeader() throws Exception {
    String[] parts = fixtureParts();
    parts[0] = encode("{\"alg\":\"RS256\",\"kid\":\"different-key\"}");
    PublicKey key = fixturePublicKey();

    assertThrows(JwsVerificationException.class, () -> verifier.verify(join(parts), key));
  }

  @Test
  void rejectsATamperedPayload() throws Exception {
    String[] parts = fixtureParts();
    parts[1] = encode("A different payload.");
    PublicKey key = fixturePublicKey();

    assertThrows(JwsVerificationException.class, () -> verifier.verify(join(parts), key));
  }

  @Test
  void rejectsATamperedSignature() throws Exception {
    String[] parts = fixtureParts();
    byte[] signature = DECODER.decode(parts[2]);
    signature[0] ^= 1;
    parts[2] = ENCODER.encodeToString(signature);
    PublicKey key = fixturePublicKey();

    assertThrows(JwsVerificationException.class, () -> verifier.verify(join(parts), key));
  }

  @Test
  void rejectsATruncatedSignature() throws Exception {
    String[] parts = fixtureParts();
    byte[] signature = DECODER.decode(parts[2]);
    parts[2] = ENCODER.encodeToString(java.util.Arrays.copyOf(signature, signature.length - 1));
    PublicKey key = fixturePublicKey();

    assertThrows(JwsVerificationException.class, () -> verifier.verify(join(parts), key));
  }

  @Test
  void rejectsTheWrongRsaPublicKey() throws Exception {
    String token = fixtureText("compact.jws").strip();

    assertThrows(
        JwsVerificationException.class, () -> verifier.verify(token, ADVERSARIAL_KEYS.getPublic()));
  }

  @Test
  void rejectsANonRsaPublicKey() throws Exception {
    String token = fixtureText("compact.jws").strip();
    PublicKey key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();

    assertThrows(JwsVerificationException.class, () -> verifier.verify(token, key));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-a-token", ".", "..", "a.b", "a.b.c.d", ".b.c", "a..c", "a.b."})
  void rejectsMalformedCompactSerialization(String token) {
    assertThrows(
        JwsVerificationException.class, () -> verifier.verify(token, ADVERSARIAL_KEYS.getPublic()));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void rejectsInvalidBase64urlInAnySegment(int segment) throws Exception {
    String[] parts = fixtureParts();
    parts[segment] = "*";
    PublicKey key = fixturePublicKey();

    assertThrows(JwsVerificationException.class, () -> verifier.verify(join(parts), key));
  }

  @ParameterizedTest
  @ValueSource(strings = {"none", "HS256", "PS256", "RS512", "ML-DSA-65"})
  void rejectsOtherAlgorithmsEvenWithAValidRsaSignature(String algorithm) throws Exception {
    String token = signWithRsa("{\"alg\":\"" + algorithm + "\"}");

    assertThrows(
        JwsVerificationException.class, () -> verifier.verify(token, ADVERSARIAL_KEYS.getPublic()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"alg\":null}", "{\"alg\":256}", "{\"alg\":[\"RS256\"]}"})
  void rejectsMissingOrNonStringAlgorithmsEvenWithAValidRsaSignature(String header)
      throws Exception {
    String token = signWithRsa(header);

    assertThrows(
        JwsVerificationException.class, () -> verifier.verify(token, ADVERSARIAL_KEYS.getPublic()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-json", "{", "[]", "null", "\"RS256\""})
  void rejectsInvalidProtectedHeaderObjectsEvenWithAValidRsaSignature(String header)
      throws Exception {
    String token = signWithRsa(header);

    assertThrows(
        JwsVerificationException.class, () -> verifier.verify(token, ADVERSARIAL_KEYS.getPublic()));
  }

  private static String signWithRsa(String header) throws GeneralSecurityException {
    String input = encode(header) + "." + encode("Test payload.");
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(ADVERSARIAL_KEYS.getPrivate());
    signer.update(input.getBytes(StandardCharsets.US_ASCII));
    return input + "." + ENCODER.encodeToString(signer.sign());
  }

  private static String encode(String text) {
    return ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  private static String[] fixtureParts() throws IOException {
    return fixtureText("compact.jws").strip().split("\\.", -1);
  }

  private static String join(String[] parts) {
    return String.join(".", parts);
  }

  private static PublicKey fixturePublicKey() throws IOException, GeneralSecurityException {
    var properties = new Properties();
    try (InputStream stream = fixtureStream("public-key.properties")) {
      properties.load(stream);
    }
    var modulus = new BigInteger(1, DECODER.decode(properties.getProperty("n")));
    var exponent = new BigInteger(1, DECODER.decode(properties.getProperty("e")));
    return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
  }

  private static String fixtureText(String name) throws IOException {
    try (InputStream stream = fixtureStream(name)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static InputStream fixtureStream(String name) {
    return Objects.requireNonNull(
        Rs256JwsVerifierTest.class.getResourceAsStream("/rs256-rfc7520/" + name),
        "Missing RFC 7520 fixture: " + name);
  }

  private static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Cannot generate RSA keys for adversarial tests", exception);
    }
  }
}
