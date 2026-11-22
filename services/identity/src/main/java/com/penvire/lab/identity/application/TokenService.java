package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.in.IssueAccessTokenUseCase;
import com.penvire.lab.identity.application.port.out.AccessTokenSigner;
import com.penvire.lab.identity.config.JwtConfig;
import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import java.time.Clock;
import java.util.UUID;

public final class TokenService implements IssueAccessTokenUseCase {
  private final JwtConfig jwtConfig;

  private final String issuer;
  private final Clock clock;
  private final AccessTokenSigner signer;

  public TokenService(JwtConfig jwtConfig, String issuer, Clock clock, AccessTokenSigner signer) {
    this.jwtConfig = jwtConfig;
    this.issuer = issuer;
    this.clock = clock;
    this.signer = signer;
  }

  @Override
  public String issueAccessToken(String subject, String clientId, String audience) {
    var now = clock.instant();

    var claims =
        new AccessTokenClaims(
            issuer,
            subject,
            clientId,
            audience,
            UUID.randomUUID().toString(),
            now,
            now,
            now.plus(jwtConfig.accessToken().lifetime()));

    return signer.sign(claims);
  }
}
