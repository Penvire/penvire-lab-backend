package com.penvire.lab.identity.application;

import com.penvire.lab.identity.application.port.in.GetJwksSetUseCase;
import com.penvire.lab.identity.application.port.in.RevokeSigningKeyUseCase;
import com.penvire.lab.identity.application.port.in.RotateSigningKeyUseCase;
import com.penvire.lab.identity.application.port.out.SigningKeyGeneratorPort;
import com.penvire.lab.identity.application.port.out.SigningKeyPersistencePort;
import com.penvire.lab.identity.config.JwksConfig;
import com.penvire.lab.identity.config.JwtConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import com.penvire.lab.identity.domain.signingkey.SigningKeyPhase;
import com.penvire.lab.persistence.IdGenerator;
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
  private final SigningKeyGeneratorPort signingKeyGenerator;
  private final IdGenerator idGenerator;

  private final JwtConfig jwtConfig;
  private final JwksConfig jwksConfig;

  public SigningKeyService(
      SigningKeyPersistencePort signingKeys,
      Clock clock,
      SigningKeyGeneratorPort signingKeyGenerator,
      IdGenerator idGenerator,
      JwtConfig jwtConfig,
      JwksConfig jwksConfig) {
    this.signingKeys = signingKeys;
    this.clock = clock;
    this.signingKeyGenerator = signingKeyGenerator;
    this.idGenerator = idGenerator;
    this.jwtConfig = jwtConfig;
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
        insertKey(alg, now, now, signingPeriod);
      } else if (pending.isEmpty()) {
        var handoff = signerKey.get().lifetime().signUntil();
        if (!now.isBefore(handoff.minus(jwksConfig.leadTime()))) {
          insertKey(alg, now, handoff, jwksConfig.signingPeriod());
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

    signingKey.ifPresent(activeSigningKey -> signingKeys.revoke(activeSigningKey.key().id()));

    cleanUp(now);
    rotate();
  }

  private void insertKey(
      SigningKeyAlgorithm alg, Instant publishAt, Instant signingAt, Duration signingPeriod) {
    var pair = signingKeyGenerator.generate(alg);
    var lifetime =
        SigningKeyLifetime.starting(
            publishAt, signingAt, signingPeriod, jwtConfig.accessToken().lifetime());
    var key = new SigningKey(idGenerator.next(), alg, pair.getPublic(), lifetime);
    signingKeys.insert(key, pair.getPrivate());
  }

  private void cleanUp(Instant now) {
    signingKeys.deleteExpiredAt(now);
    signingKeys.erasePrivateKeysAt(now);
  }
}
