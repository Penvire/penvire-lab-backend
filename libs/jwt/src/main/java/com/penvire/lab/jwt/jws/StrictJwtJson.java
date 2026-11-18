package com.penvire.lab.jwt.jws;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class StrictJwtJson {
  private static final int MAX_NAME_LENGTH = 128;
  private static final int MAX_STRING_LENGTH = 4096;

  private static final ObjectMapper MAPPER =
      JsonMapper.builder(
              JsonFactory.builder()
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(16)
                          .maxNameLength(MAX_NAME_LENGTH)
                          .maxNumberLength(64)
                          .maxStringLength(MAX_STRING_LENGTH)
                          .build())
                  .build())
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private StrictJwtJson() {}

  public static JsonNode read(byte[] bytes) throws IOException {
    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    JsonNode node = MAPPER.readTree(text);
    if (node == null) {
      return MissingNode.getInstance();
    }

    checkTextLengths(node);
    return node;
  }

  private static void checkTextLengths(JsonNode node) throws IOException {
    if (node.isTextual() && node.textValue().length() > MAX_STRING_LENGTH) {
      throw new IOException("JWT JSON string is too long");
    }

    if (node.isObject()) {
      for (var property : node.properties()) {
        if (property.getKey().length() > MAX_NAME_LENGTH) {
          throw new IOException("JWT JSON property name is too long");
        }
        checkTextLengths(property.getValue());
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        checkTextLengths(child);
      }
    }
  }
}
