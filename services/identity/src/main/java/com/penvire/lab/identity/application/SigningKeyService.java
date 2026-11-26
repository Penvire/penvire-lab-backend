package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.in.signingkey.GetJwksSetUseCase;
import com.penvire.lab.identity.application.port.in.signingkey.RevokeSigningKeyUseCase;
import com.penvire.lab.identity.application.port.in.signingkey.RotateSigningKeyUseCase;
import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyPersistencePort;
import com.penvire.lab.identity.config.JwksConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyPhase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class SigningKeyService
    implements GetJwksSetUseCase, RotateSigningKeyUseCase, RevokeSigningKeyUseCase {
  private final SigningKeyPersistencePort signingKeys;
  private final Clock clock;
  private final SigningKeyLifecycle lifecycle;

  private final JwksConfig jwksConfig;

  public SigningKeyService(
      SigningKeyPersistencePort signingKeys,
      Clock clock,
      SigningKeyLifecycle lifecycle,
      JwksConfig jwksConfig) {
    this.signingKeys = signingKeys;
    this.clock = clock;
    this.lifecycle = lifecycle;
    this.jwksConfig = jwksConfig;
  }

  /** The use case owns the transaction; the persistence adapter requires one. */
  @Override
  @Transactional
  public List<SigningKey> getJwksSet() {
    return signingKeys.findPublishedAt(clock.instant());
  }

  /**
   * One run for every algorithm in use; the lock keeps two instances from filling the same slot.
   */
  @Override
  @Transactional
  public void rotate() {
    signingKeys.lockRotation();
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var publishedKeys = signingKeys.findPublishedAt(now);

    for (var alg : jwksConfig.algorithms()) {
      var algKeys = publishedKeys.stream().filter(key -> key.algorithm() == alg).toList();
      var signerKey =
          algKeys.stream().filter(key -> key.phaseAt(now) == SigningKeyPhase.SIGNING).findFirst();
      var pending =
          algKeys.stream().filter(key -> key.phaseAt(now) == SigningKeyPhase.PENDING).findFirst();

      if (signerKey.isEmpty()) {
        var signingPeriod =
            pending
                .map(key -> Duration.between(now, key.lifetime().signFrom()))
                .orElseGet(jwksConfig::signingPeriod);
        lifecycle.create(alg, now, now, signingPeriod);
      } else if (pending.isEmpty()) {
        var handoff = signerKey.get().lifetime().signUntil();
        if (!now.isBefore(handoff.minus(jwksConfig.leadTime()))) {
          lifecycle.create(alg, now, handoff, jwksConfig.signingPeriod());
        }
      }
    }

    cleanUp(now);
  }

  @Override
  @Transactional
  public void revoke(SigningKeyAlgorithm algorithm) {
    signingKeys.lockRotation();
    var now = clock.instant();
    var signingKey = signingKeys.findSignerAt(algorithm, now);

    signingKey.ifPresent(activeSigningKey -> lifecycle.revoke(activeSigningKey.key(), now));

    cleanUp(now);
    rotate();
  }

  private void cleanUp(Instant now) {
    signingKeys.deleteExpiredAt(now);
    signingKeys.erasePrivateKeysAt(now);
  }
}
