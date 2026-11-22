package com.penvire.lab.identity.adapter.out.signing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.penvire.lab.identity.application.port.out.AccessTokenSigner;
import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;

public class MLDsa65JwsSigner implements AccessTokenSigner {
  private static final String ALGORITHM = "ML-DSA-65";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private final String keyId;
  private final PrivateKey privateKey;

  public MLDsa65JwsSigner(String keyId, PrivateKey privateKey) {
    if (keyId.isBlank()) {
      throw new IllegalArgumentException("keyId cannot be null");
    }

    if (!(privateKey.getParams() instanceof NamedParameterSpec params)
        || !ALGORITHM.equals(params.getName())) {
      throw new IllegalArgumentException("Signing requires an ML-DSA-65 private key");
    }

    this.keyId = keyId;
    this.privateKey = privateKey;
  }

  @Override
  public String sign(AccessTokenClaims claims) {
    var header =
        JSON.createObjectNode().put("alg", ALGORITHM).put("typ", "at+jwt").put("kid", keyId);

    var payload =
        JSON.createObjectNode()
            .put("iss", claims.issuer())
            .put("sub", claims.subject())
            .put("client_id", claims.clientId())
            .put("aud", claims.audience())
            .put("jti", claims.tokenId())
            .put("iat", claims.issuedAt().getEpochSecond())
            .put("nbf", claims.notBefore().getEpochSecond())
            .put("exp", claims.expiresAt().getEpochSecond());

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
      return input + "." + BASE64URL.encodeToString(signInput(input));
    } catch (JsonProcessingException | GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to sign access token", exception);
    }
  }

  private byte[] signInput(String input) throws GeneralSecurityException {
    var signature = Signature.getInstance(ALGORITHM);
    signature.initSign(privateKey);
    signature.update(input.getBytes(StandardCharsets.US_ASCII));
    return signature.sign();
  }
}
