package com.penvire.lab.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.penvire.lab.jwt.validation.VerifiedJwt;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public record BearerPrincipal(
    String issuer,
    String subject,
    String clientId,
    String tokenId,
    Set<String> audiences,
    Set<String> scopes,
    Instant expiresAt)
    implements Principal {

  public BearerPrincipal {
    requireText("issuer", issuer);
    requireText("subject", subject);
    requireText("clientId", clientId);
    requireText("tokenId", tokenId);
    Objects.requireNonNull(expiresAt, "expiresAt");

    audiences = Set.copyOf(audiences);
    scopes = Set.copyOf(scopes);

    if (audiences.isEmpty()) {
      throw new IllegalArgumentException("audiences must not be empty");
    }
  }

  @Override
  public String getName() {
    return subject;
  }

  public static BearerPrincipal from(VerifiedJwt verifiedJwt) {
    Objects.requireNonNull(verifiedJwt, "verifiedJwt");
    JsonNode claims = verifiedJwt.claims();

    return new BearerPrincipal(
        requiredText(claims, "iss"),
        requiredText(claims, "sub"),
        requiredText(claims, "client_id"),
        requiredText(claims, "jti"),
        parseAudiences(claims.get("aud")),
        parseScopes(claims.get("scope")),
        Instant.ofEpochSecond(claims.get("exp").asLong()));
  }

  private static void requireText(String name, String value) {
    Objects.requireNonNull(value, name);

    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private static String requiredText(JsonNode claims, String name) {
    JsonNode node = claims.get(name);

    if (node == null || !node.isTextual() || node.textValue().isBlank()) {
      throw new IllegalArgumentException("Missing or invalid claim: " + name);
    }

    return node.textValue();
  }

  private static Set<String> parseAudiences(JsonNode aud) {
    if (aud.isTextual()) {
      return Set.of(aud.textValue());
    }

    if (aud.isArray()) {
      Set<String> result = new HashSet<>();
      for (JsonNode entry : aud) {
        if (entry.isTextual() && !entry.textValue().isBlank()) {
          result.add(entry.textValue());
        }
      }
      return Set.copyOf(result);
    }
    throw new IllegalArgumentException("Invalid claim: aud");
  }

  private static Set<String> parseScopes(@Nullable JsonNode scope) {
    if (scope == null || scope.isNull()) {
      return Set.of();
    }

    if (!scope.isTextual()) {
      throw new IllegalArgumentException("Invalid claim: scope");
    }

    String raw = scope.textValue().trim();
    if (raw.isEmpty()) {
      return Set.of();
    }

    return Arrays.stream(raw.split("\\s+"))
        .filter(token -> !token.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }
}
