package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.in.IssueAccessTokenUseCase;
import com.penvire.lab.identity.application.port.out.SigningKeyPersistencePort;
import com.penvire.lab.identity.application.port.out.TokenSignerPort;
import com.penvire.lab.identity.config.IdentityConfig;
import com.penvire.lab.identity.config.JwtConfig;
import com.penvire.lab.identity.domain.jwt.AccessTokenClaims;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class TokenService implements IssueAccessTokenUseCase {
  private final JwtConfig jwtConfig;

  private final String issuer;
  private final Clock clock;
  private final TokenSignerPort signer;

  private final SigningKeyPersistencePort signingKeyPersistencePort;

  public TokenService(
      JwtConfig jwtConfig,
      IdentityConfig identityConfig,
      Clock clock,
      TokenSignerPort signer,
      SigningKeyPersistencePort signingKeyPersistencePort) {
    this.jwtConfig = jwtConfig;
    this.issuer = identityConfig.issuer().toString();
    this.clock = clock;
    this.signer = signer;
    this.signingKeyPersistencePort = signingKeyPersistencePort;
  }

  @Override
  public String issueAccessToken(String subject, String clientId, String audience) {
    return issueAccessToken(subject, clientId, audience, SigningKeyAlgorithm.ML_DSA_65);
  }

  @Override
  @Transactional
  public String issueAccessToken(
      String subject, String clientId, String audience, SigningKeyAlgorithm algorithm) {
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

    ActiveSigningKey signingKey =
        signingKeyPersistencePort
            .findSignerAt(algorithm, now)
            .orElseThrow(() ->
                new IllegalStateException("No " + algorithm.joseName() + " key signs at " + now));

    return signer.sign(claims, signingKey);
  }
}
