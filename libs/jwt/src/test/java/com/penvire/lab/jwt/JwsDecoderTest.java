package com.penvire.lab.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.jwt.jws.JwsDecoder;
import com.penvire.lab.jwt.jws.JwsVerificationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwsDecoderTest {

  private final JwsDecoder decoder = new JwsDecoder();

  @Test
  void decodesTheIndependentFixtureWithoutChangingItsSigningInput() throws Exception {
    String token = JwtTestTokens.fixtureText("compact.jws");
    ParsedJws parsed = decoder.decode(token);
    String[] parts = token.split("\\.", -1);

    assertEquals("RS256", parsed.header().path("alg").textValue());
    assertEquals("bilbo.baggins@hobbiton.example", parsed.header().path("kid").textValue());
    assertArrayEquals(
        JwtTestTokens.fixtureText("payload.txt").getBytes(StandardCharsets.UTF_8),
        parsed.payload());
    assertArrayEquals(
        (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII), parsed.signingInput());
    assertArrayEquals(Base64.getUrlDecoder().decode(parts[2]), parsed.signature());
  }

  @Test
  void preservesHeaderWhitespaceAndOrderingInTheSigningInput() {
    String header = JwtTestTokens.encode("{ \"kid\" : \"key\", \"alg\" : \"RS256\" }");
    String payload = JwtTestTokens.encode("payload");

    ParsedJws parsed = decoder.decode(header + "." + payload + ".AQ");

    assertArrayEquals(
        (header + "." + payload).getBytes(StandardCharsets.US_ASCII), parsed.signingInput());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\n\t"})
  void rejectsBlankTokens(String token) {
    assertThrows(JwsVerificationException.class, () -> decoder.decode(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "a.b", "a.b.c.d", ".", "..", ".b.c", "a..c", "a.b."})
  void rejectsInvalidCompactStructure(String token) {
    assertThrows(JwsVerificationException.class, () -> decoder.decode(token));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void rejectsInvalidBase64urlInEachSegment(int segment) {
    String[] parts = {JwtTestTokens.encode("{}"), "AQ", "AQ"};
    parts[segment] = "*";

    var failure =
        assertThrows(JwsVerificationException.class, () -> decoder.decode(String.join(".", parts)));

    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "null", "[]", "true", "1", "\"header\""})
  void requiresAHeaderObject(String header) {
    assertThrows(
        JwsVerificationException.class,
        () -> decoder.decode(JwtTestTokens.encode(header) + ".AQ.AQ"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "{\"alg\":\"RS256\",\"alg\":\"none\"}", "{} {}"})
  void rejectsMalformedDuplicateOrTrailingHeaderJson(String header) {
    var failure =
        assertThrows(
            JwsVerificationException.class,
            () -> decoder.decode(JwtTestTokens.encode(header) + ".AQ.AQ"));

    assertInstanceOf(IOException.class, failure.getCause());
  }
}
