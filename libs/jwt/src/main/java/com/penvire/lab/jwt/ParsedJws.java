package com.penvire.lab.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Parsed JWS data; parsing alone does not establish authenticity. */
public final class ParsedJws {

  private final JsonNode header;
  private final byte[] payload;
  private final byte[] signingInput;
  private final byte[] signature;

  public ParsedJws(JsonNode header, byte[] payload, byte[] signingInput, byte[] signature) {
    this.header = Objects.requireNonNull(header, "header").deepCopy();
    this.payload = Objects.requireNonNull(payload, "payload").clone();
    this.signingInput = Objects.requireNonNull(signingInput, "signingInput").clone();
    this.signature = Objects.requireNonNull(signature, "signature").clone();
  }

  JsonNode header() {
    return header.deepCopy();
  }

  byte[] payload() {
    return payload.clone();
  }

  byte[] signingInput() {
    return signingInput.clone();
  }

  byte[] signature() {
    return signature.clone();
  }
}
