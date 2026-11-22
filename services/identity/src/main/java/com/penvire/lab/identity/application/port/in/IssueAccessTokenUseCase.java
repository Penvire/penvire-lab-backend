package com.penvire.lab.identity.application.port.in;

public interface IssueAccessTokenUseCase {
  /** Returns a signed compact JWS access token. */
  String issueAccessToken(String subject, String clientId, String audience);
}
