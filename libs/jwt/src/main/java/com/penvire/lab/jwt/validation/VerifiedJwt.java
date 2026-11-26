package com.penvire.lab.jwt.validation;

import com.fasterxml.jackson.databind.JsonNode;

/** JWT claims that passed signature and configured profile validation. */
public final class VerifiedJwt {

  private final JsonNode claims;

  VerifiedJwt(JsonNode claims) {
    this.claims = claims;
  }

  public JsonNode claims() {
    return claims.deepCopy();
  }
}
