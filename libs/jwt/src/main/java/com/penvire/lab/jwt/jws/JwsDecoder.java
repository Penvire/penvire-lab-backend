package com.penvire.lab.jwt.jws;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwsDecoder {
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  public ParsedJws decode(String jwt) {
    if (jwt.isBlank()) {
      throw new JwsVerificationException("Missing compact token.");
    }

    String[] parts = jwt.split("\\.", -1);
    if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
      throw new JwsVerificationException("Malformed JWT compact token.");
    }

    try {
      byte[] headerBytes = decodeSegment(parts[0], 2 * 1024);
      byte[] payload = decodeSegment(parts[1], 16 * 1024);
      byte[] signature = decodeSegment(parts[2], 4 * 1024);

      JsonNode header = StrictJwtJson.read(headerBytes);
      if (!header.isObject()) {
        throw new JwsVerificationException("JWS Header must be a JSON object.");
      }

      byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
      return new ParsedJws(header, payload, signingInput, signature);
    } catch (IllegalArgumentException | IOException exception) {
      throw new JwsVerificationException("Cannot decode compact JWS", exception);
    }
  }

  private static byte[] decodeSegment(String segment, int maxBytes) {
    int maxEncodedChars = (4 * maxBytes + 2) / 3;
    if (segment.length() > maxEncodedChars) {
      throw new JwsVerificationException("Compact JWS segment is too large");
    }

    byte[] decoded = URL_DECODER.decode(segment);
    if (decoded.length > maxBytes) {
      throw new JwsVerificationException("Compact JWS segment is too large");
    }
    if (!segment.equals(URL_ENCODER.encodeToString(decoded))) {
      throw new JwsVerificationException("Compact JWS segment is not canonical base64url");
    }
    return decoded;
  }
}
