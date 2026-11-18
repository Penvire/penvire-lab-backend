package com.penvire.lab.jwt.jws;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwsDecoder {
  private static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  public ParsedJws decode(String jwt) {
    if (jwt.isBlank()) {
      throw new JwsVerificationException("Missing compact token.");
    }

    String[] parts = jwt.split("\\.", -1);
    if(parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
      throw new JwsVerificationException("Malformed JWT compact token.");
    }

    try {
      Base64.Decoder decoder = Base64.getUrlDecoder();

      JsonNode header = OBJECT_MAPPER.readTree(decoder.decode(parts[0]));
      if (header == null || !header.isObject()) {
        throw new JwsVerificationException("JWS Header must be a JSON object.");
      }

      byte[] payload = decoder.decode(parts[1]);
      byte[] signature = decoder.decode(parts[2]);

      byte[] signingInput =
          (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);

      return new ParsedJws(header, payload, signingInput, signature);
    } catch (IllegalArgumentException | IOException e) {
      throw new JwsVerificationException("Cannot decode compact JWS", e);    }
  }
}
