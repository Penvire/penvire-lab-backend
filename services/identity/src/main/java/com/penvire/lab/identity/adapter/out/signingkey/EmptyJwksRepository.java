package com.penvire.lab.identity.adapter.out.signingkey;

import com.penvire.lab.identity.application.port.out.JwksRepository;
import com.penvire.lab.identity.domain.signingkey.PublicSigningKey;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

/**
 * Placeholder until signing keys are persisted: publishes no keys. Delete it together with adding
 * the persistence adapter; while both exist, Quarkus fails at startup on an ambiguous dependency.
 */
@ApplicationScoped
public class EmptyJwksRepository implements JwksRepository {
  @Override
  public List<PublicSigningKey> findAllPublishablePublicKeys(Instant now) {
    return List.of();
  }
}
