package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.out.AccessTokenSigner;
import com.penvire.lab.identity.domain.AccessTokenClaims;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

public final class IssueAccessToken {
  private static final Duration LIFETIME = Duration.ofMinutes(5);

  private final String issuer;
  private final Clock clock;
  private final AccessTokenSigner signer;

  public IssueAccessToken(String issuer, Clock clock, AccessTokenSigner signer) {
    this.issuer = issuer;
    this.clock = clock;
    this.signer = signer;
  }

  public String issue(String subject, String clientId, String audience) {
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
            now.plus(LIFETIME));

    return signer.sign(claims);
  }
}
