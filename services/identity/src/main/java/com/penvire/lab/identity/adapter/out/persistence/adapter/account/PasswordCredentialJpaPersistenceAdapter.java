package com.penvire.lab.identity.adapter.out.persistence.adapter.account;

import com.penvire.lab.identity.adapter.out.persistence.entity.account.PasswordCredentialEntity;
import com.penvire.lab.identity.adapter.out.persistence.entity.account.PasswordCredentialEntityRepository;
import com.penvire.lab.identity.adapter.out.persistence.mapper.account.PasswordCredentialPersistenceMapping;
import com.penvire.lab.identity.application.port.out.PasswordCredentialPersistencePort;
import com.penvire.lab.identity.domain.account.PasswordCredential;
import com.penvire.lab.persistence.DomainRepository;
import com.penvire.lab.persistence.EntityMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PasswordCredentialJpaPersistenceAdapter
    extends DomainRepository<PasswordCredential, PasswordCredentialEntity, UUID>
    implements PasswordCredentialPersistencePort {

  private final PasswordCredentialEntityRepository entities;
  private final PasswordCredentialPersistenceMapping mapper;

  public PasswordCredentialJpaPersistenceAdapter(
      PasswordCredentialEntityRepository entities, PasswordCredentialPersistenceMapping mapper) {
    this.entities = entities;
    this.mapper = mapper;
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public Optional<PasswordCredential> findActiveByAccountId(UUID accountId) {
    return entities
        .find("accountId = ?1 and revokedAt is null", accountId)
        .firstResultOptional()
        .map(mapper::toDomain);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public List<PasswordCredential> findRecentByAccountId(UUID accountId, int limit) {
    return entities
        .find("accountId = ?1 order by createdAt desc", accountId)
        .page(0, limit)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  protected PanacheRepositoryBase<PasswordCredentialEntity, UUID> entities() {
    return entities;
  }

  @Override
  protected EntityMapper<PasswordCredential, PasswordCredentialEntity> mapper() {
    return mapper;
  }

  @Override
  protected UUID idOf(PasswordCredential domain) {
    return domain.id();
  }
}
