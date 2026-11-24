package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.in.GetJwksSetUseCase;
import com.penvire.lab.identity.application.port.out.SigningKeyPort;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;

@ApplicationScoped
public class SigningKeyService implements GetJwksSetUseCase {
  private final SigningKeyPort jwksRepository;
  private final Clock clock;

  public SigningKeyService(SigningKeyPort jwksRepository, Clock clock) {
    this.jwksRepository = jwksRepository;
    this.clock = clock;
  }

  /** The use case owns the transaction; the persistence adapter requires one. */
  @Override
  @Transactional
  public List<SigningKey> getJwksSet() {
    return jwksRepository.findPublishedAt(clock.instant());
  }
}
