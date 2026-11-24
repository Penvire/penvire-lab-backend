package com.penvire.lab.identity.adapter.out.persistence.adapter;

import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntity;
import com.penvire.lab.identity.adapter.out.persistence.entity.SigningKeyEntityRepository;
import com.penvire.lab.identity.adapter.out.persistence.mapper.SigningKeyPersistenceMapper;
import com.penvire.lab.identity.application.port.out.SealedPrivateKey;
import com.penvire.lab.identity.application.port.out.SigningKeyPort;
import com.penvire.lab.identity.application.port.out.SigningKeyProtectorPort;
import com.penvire.lab.identity.domain.signingkey.ActiveSigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKey;
import com.penvire.lab.identity.domain.signingkey.SigningKeyAlgorithm;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores signing keys. Never writes a row without sealing it and never returns one whose MAC does
 * not match. Callers own the transaction.
 */
@ApplicationScoped
public class SigningKeyPersistenceAdapter implements SigningKeyPort {

  private final SigningKeyEntityRepository entities;
  private final SigningKeyPersistenceMapper mapper;
  private final SigningKeyProtectorPort protector;

  public SigningKeyPersistenceAdapter(SigningKeyEntityRepository entities, SigningKeyPersistenceMapper mapper, SigningKeyProtectorPort protector) {
    this.entities = entities;
    this.mapper = mapper;
    this.protector = protector;
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public void insert(SigningKey key, PrivateKey privateKey) {
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
  public void update(SigningKey key) {
    var entity =
        entities
            .findByIdOptional(key.id(), LockModeType.PESSIMISTIC_WRITE)
            .filter(stored -> verified(stored).isPresent())
            .orElseThrow(() -> new IllegalStateException("No intact signing key " + key.kid()));
    mapper.updateEntity(key, entity);

    if (key.lifetime().revokedAt() != null) {
      entity.setEncryptedPrivateKey(null);
      entity.setPrivateKeyNonce(null);
    }

    // The MAC covers the row as stored, not the key passed in.
    entity.setRowMac(protector.mac(mapper.toDomain(entity), entity.getKekId()));
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public List<SigningKey> findPublishedAt(Instant now) {
    return entities
        .find(
            "publishFrom <= ?1 and publishUntil > ?1 and (revokedAt is null or revokedAt > ?1)",
            now)
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
                    + " and (revokedAt is null or revokedAt > ?2)"
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
        + " and (signUntil <= ?1 or revokedAt <= ?1)",
        now);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public int deleteExpiredAt(Instant now) {
    return (int) entities.delete("publishUntil <= ?1", now);
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
