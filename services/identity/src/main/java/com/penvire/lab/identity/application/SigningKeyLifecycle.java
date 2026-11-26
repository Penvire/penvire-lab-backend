package com.penvire.lab.identity.application;

import com.penvire.lab.audit.AuditScope;
import com.penvire.lab.audit.Audited;
import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyGeneratorPort;
import com.penvire.lab.identity.application.port.out.signingkey.SigningKeyPersistencePort;
import com.penvire.lab.identity.config.JwtConfig;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import com.penvire.lab.identity.domain.signingkey.SigningKeyLifetime;
import com.penvire.lab.persistence.IdGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;

/** Audited per-key transitions invoked inside SigningKeyService's transactions */
@ApplicationScoped
public class SigningKeyLifecycle {

  private final SigningKeyPersistencePort signingKeys;
  private final SigningKeyGeneratorPort generator;
  private final IdGenerator idGenerator;
  private final JwtConfig jwtConfig;
  private final AuditScope audit;

  public SigningKeyLifecycle(
      SigningKeyPersistencePort signingKeys,
      SigningKeyGeneratorPort generator,
      IdGenerator idGenerator,
      JwtConfig jwtConfig,
      AuditScope audit) {
    this.signingKeys = signingKeys;
    this.generator = generator;
    this.idGenerator = idGenerator;
    this.jwtConfig = jwtConfig;
    this.audit = audit;
  }

  @Audited(value = "identity.signing-key.create", state = SigningKey.class, ignore = "publicKey")
  void create(
      SigningKeyAlgorithm alg, Instant publishAt, Instant signingAt, Duration signingPeriod) {
    var pair = generator.generate(alg);
    var lifetime =
        SigningKeyLifetime.starting(
            publishAt, signingAt, signingPeriod, jwtConfig.accessToken().lifetime());

    var key = new SigningKey(idGenerator.next(), alg, pair.getPublic(), lifetime);
    signingKeys.insert(key, pair.getPrivate());

    audit.target(key.id().toString());
    audit.state(null, key);
  }

  @Audited(value = "identity.signing-key.revoke", state = SigningKey.class, ignore = "publicKey")
  void revoke(SigningKey key, Instant revokedAt) {
    signingKeys.revoke(key.id());

    audit.target(key.id().toString());
    audit.state(key, key.revokeAt(revokedAt));
  }
}
