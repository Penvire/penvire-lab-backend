package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.in.GetJwksSetUseCase;
import com.penvire.lab.identity.application.port.out.JwksRepository;
import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.List;

@ApplicationScoped
public class JwksService implements GetJwksSetUseCase {
  private final JwksRepository jwksRepository;
  private final Clock clock;

  public JwksService(JwksRepository jwksRepository, Clock clock) {
    this.jwksRepository = jwksRepository;
    this.clock = clock;
  }

  @Override
  public List<PublicSigningKey> getJwksSet() {
    return jwksRepository.findAllPublishablePublicKeys(clock.instant());
  }
}
