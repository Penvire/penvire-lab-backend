package com.penvire.lab.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ParsedJwsTest {

  @Test
  void snapshotsMutableConstructorInputs() {
    var header = JwtTestTokens.header(JwtType.ACCESS, "RS256");
    byte[] payload = {1};
    byte[] input = {2};
    byte[] signature = {3};
    var parsed = new ParsedJws(header, payload, input, signature);

    header.put("alg", "none");
    payload[0] = 9;
    input[0] = 9;
    signature[0] = 9;

    assertEquals("RS256", parsed.header().path("alg").textValue());
    assertArrayEquals(new byte[] {1}, parsed.payload());
    assertArrayEquals(new byte[] {2}, parsed.signingInput());
    assertArrayEquals(new byte[] {3}, parsed.signature());
  }

  @Test
  void returnsIndependentCopiesFromAccessors() {
    var parsed =
        new ParsedJws(
            JwtTestTokens.header(JwtType.ACCESS, "RS256"),
            new byte[] {1},
            new byte[] {2},
            new byte[] {3});

    parsed.header().withObject("/nested").put("value", "changed");
    parsed.payload()[0] = 9;
    parsed.signingInput()[0] = 9;
    parsed.signature()[0] = 9;

    assertEquals(0, parsed.header().path("nested").size());
    assertArrayEquals(new byte[] {1}, parsed.payload());
    assertArrayEquals(new byte[] {2}, parsed.signingInput());
    assertArrayEquals(new byte[] {3}, parsed.signature());
  }
}
