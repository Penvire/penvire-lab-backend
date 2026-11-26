package com.penvire.lab.identity.application.port.in.jwt;

import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;

public interface IssueAccessTokenUseCase {
  /** Returns a signed compact JWS access token. */
  String issueAccessToken(String subject, String clientId, String audience);

  /* Returns a signed compact JWS access token with specific algorithm. */
  String issueAccessToken(
      String subject, String clientId, String audience, SigningKeyAlgorithm algorithm);
}
