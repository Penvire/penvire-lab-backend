package com.penvire.lab.identity.adapter.out.persistence.adapter.account;

import com.penvire.lab.identity.adapter.out.persistence.entity.account.AccountEntity;
import com.penvire.lab.identity.adapter.out.persistence.entity.account.AccountEntityRepository;
import com.penvire.lab.identity.adapter.out.persistence.mapper.account.AccountPersistenceMapper;
import com.penvire.lab.identity.application.port.out.account.AccountPersistencePort;
import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.Username;
import com.penvire.lab.persistence.DomainRepository;
import com.penvire.lab.persistence.EntityMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AccountJpaPersistenceAdapter extends DomainRepository<Account, AccountEntity, UUID>
    implements AccountPersistencePort {

  private final AccountEntityRepository entities;
  private final AccountPersistenceMapper mapper;

  public AccountJpaPersistenceAdapter(
      AccountEntityRepository entities, AccountPersistenceMapper mapper) {
    this.entities = entities;
    this.mapper = mapper;
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public Optional<Account> findByUsername(Username username) {
    return entities.find("username", username.value()).firstResultOptional().map(mapper::toDomain);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public Optional<Account> findByEmail(Email email) {
    return entities.find("email", email.value()).firstResultOptional().map(mapper::toDomain);
  }

  @Override
  protected PanacheRepositoryBase<AccountEntity, UUID> entities() {
    return entities;
  }

  @Override
  protected EntityMapper<Account, AccountEntity> mapper() {
    return mapper;
  }

  @Override
  protected UUID idOf(Account domain) {
    return domain.id();
  }
}
