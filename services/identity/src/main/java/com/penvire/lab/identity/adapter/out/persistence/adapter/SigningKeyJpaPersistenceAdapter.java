package com.penvire.lab.identity.adapter.out.persistence.adapter;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntity;
import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntityRepository;
import com.penvire.lab.identity.adapter.out.persistence.mapper.SigningKeyPersistenceMapper;
import com.penvire.lab.identity.application.port.out.SealedPrivateKey;
import com.penvire.lab.identity.application.port.out.SigningKeyPersistencePort;
import com.penvire.lab.identity.application.port.out.SigningKeyProtectorPort;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;

/**
 * Stores signing keys. Never writes a row without sealing it and never returns one whose MAC does
 * not match. Callers own the transaction.
 */
@ApplicationScoped
public class SigningKeyJpaPersistenceAdapter implements SigningKeyPersistencePort {

  private final SigningKeyEntityRepository entities;
  private final SigningKeyPersistenceMapper mapper;
  private final SigningKeyProtectorPort protector;
  private final Clock clock;

  // Stable lock ID shared by all Identity instances using this database.
  private static final long SIGNING_KEY_ROTATION_LOCK_ID = 0x50454E565F4A574BL;

  public SigningKeyJpaPersistenceAdapter(
      SigningKeyEntityRepository entities,
      SigningKeyPersistenceMapper mapper,
      SigningKeyProtectorPort protector,
      Clock clock) {
    this.entities = entities;
    this.mapper = mapper;
    this.protector = protector;
    this.clock = clock;
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public void insert(SigningKey key, PrivateKey privateKey) {
    if (key.lifetime().isRevoked()) {
      throw new IllegalArgumentException("Cannot insert a revoked signing key " + key.kid());
    }
    var sealed = protector.seal(key, privateKey);
    var entity = mapper.toEntity(key);
    entity.setKekId(sealed.kekId());
    entity.setPrivateKeyNonce(sealed.nonce());
    entity.setEncryptedPrivateKey(sealed.ciphertext());
    entity.setRowMac(protector.mac(key, sealed.kekId()));
    entities.persist(entity);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public void retire(UUID id, Duration retention) {
    var entity = lock(id);
    var retired = requireIntact(entity).retireAt(clock.instant(), retention);
    storeLifetime(entity, retired);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public void revoke(UUID id) {
    var entity = lock(id);
    var revoked = requireIntact(entity).revokeAt(clock.instant());
    entity.setEncryptedPrivateKey(null);
    entity.setPrivateKeyNonce(null);
    storeLifetime(entity, revoked);
  }

  private SigningKeyEntity lock(UUID id) {
    var entity =
        entities
            .findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
            .orElseThrow(() -> new IllegalStateException("No signing key " + id));
    // Reload under the lock in case an earlier read cached the entity in this transaction.
    entities.flush();
    entities.getEntityManager().refresh(entity);
    return entity;
  }

  private SigningKey requireIntact(SigningKeyEntity entity) {
    return verified(entity)
        .orElseThrow(() -> new IllegalStateException("No intact signing key " + entity.getId()));
  }

  private void storeLifetime(SigningKeyEntity entity, SigningKey key) {
    mapper.updateEntity(key, entity);
    entity.setRowMac(protector.mac(key, entity.getKekId()));
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public List<SigningKey> findPublishedAt(Instant now) {
    return entities
        .find("publishFrom <= ?1 and publishUntil > ?1 and revokedAt is null", now)
        .stream()
        .flatMap(entity -> verified(entity).stream())
        .filter(key -> key.isPublishedAt(now))
        .toList();
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public Optional<ActiveSigningKey> findSignerAt(SigningKeyAlgorithm algorithm, Instant now) {
    List<SigningKeyEntity> signers =
        entities
            .find(
                "signingKeyAlgorithm = ?1 and signFrom <= ?2 and signUntil > ?2"
                    + " and revokedAt is null"
                    + " and encryptedPrivateKey is not null",
                algorithm,
                now)
            .stream()
            .filter(entity -> verified(entity).isPresent())
            .toList();
    if (signers.size() > 1) {
      throw new IllegalStateException("More than one " + algorithm + " key signs at " + now);
    }
    return signers.stream()
        .findFirst()
        .map(this::activate)
        .filter(active -> active.key().signsAt(now));
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public int erasePrivateKeysAt(Instant now) {
    // The ciphertext is not part of the row MAC, so erasing it needs no new MAC.
    return entities.update(
        "encryptedPrivateKey = null, privateKeyNonce = null"
            + " where encryptedPrivateKey is not null"
            + " and (signUntil <= ?1 or revokedAt is not null)",
        now);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public int deleteExpiredAt(Instant now) {
    return (int) entities.delete("publishUntil <= ?1", now);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public void lockRotation() {
    entities
        .getEntityManager()
        .unwrap(Session.class)
        .doWork(connection -> {
          try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
            statement.setLong(1, SIGNING_KEY_ROTATION_LOCK_ID);
            statement.execute();
          }
        });
  }

  private Optional<SigningKey> verified(SigningKeyEntity entity) {
    SigningKey key;
    try {
      key = mapper.toDomain(entity);
    } catch (IllegalArgumentException | IllegalStateException e) {
      Log.errorf(e, "Signing key %s is malformed and ignored", entity.getId());
      return Optional.empty();
    }
    if (!protector.isIntact(key, entity.getKekId(), entity.getRowMac())) {
      Log.errorf("Signing key %s fails its row MAC and is ignored", entity.getId());
      return Optional.empty();
    }
    return Optional.of(key);
  }

  private ActiveSigningKey activate(SigningKeyEntity entity) {
    byte[] ciphertext = entity.getEncryptedPrivateKey();
    byte[] nonce = entity.getPrivateKeyNonce();
    if (ciphertext == null || nonce == null) {
      throw new IllegalStateException("Signing key " + entity.getId() + " has no private part");
    }
    var key = mapper.toDomain(entity);
    var sealed = new SealedPrivateKey(entity.getKekId(), nonce, ciphertext);
    return new ActiveSigningKey(key, protector.unseal(key, sealed));
  }
}
