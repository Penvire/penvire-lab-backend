package com.penvire.lab.identity.adapter.out.crypto.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.penvire.lab.identity.application.port.out.jwt.TokenSignerPort;
import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Base64;

@ApplicationScoped
public class AccessTokenSignerAdapter implements TokenSignerPort {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private final Signer signer;

  public AccessTokenSignerAdapter(Signer signer) {
    this.signer = signer;
  }

  @Override
  public String sign(AccessTokenClaims sealed, ActiveSigningKey key) {
    SigningKeyAlgorithm algorithm = key.key().algorithm();

    var header =
        JSON.createObjectNode()
            .put("alg", algorithm.joseName())
            .put("typ", "at+jwt")
            .put("kid", key.key().kid());

    var payload =
        JSON.createObjectNode()
            .put("iss", sealed.issuer())
            .put("sub", sealed.subject())
            .put("client_id", sealed.clientId())
            .put("aud", sealed.audience())
            .put("jti", sealed.tokenId())
            .put("iat", sealed.issuedAt().getEpochSecond())
            .put("nbf", sealed.notBefore().getEpochSecond())
            .put("exp", sealed.expiresAt().getEpochSecond());

    try {
      var headerBytes = JSON.writeValueAsBytes(header);
      var payloadBytes = JSON.writeValueAsBytes(payload);

      if (headerBytes.length > 2 * 1024) {
        throw new IllegalArgumentException("JWS header exceeds 2 KiB");
      }
      if (payloadBytes.length > 16 * 1024) {
        throw new IllegalArgumentException("JWS payload exceeds 16 KiB");
      }

      var input =
          BASE64URL.encodeToString(headerBytes) + "." + BASE64URL.encodeToString(payloadBytes);
      return input + "." + BASE64URL.encodeToString(signer.sign(input, key));
    } catch (JsonProcessingException | IllegalStateException exception) {
      throw new IllegalStateException("Failed to sign access token", exception);
    }
  }
}
