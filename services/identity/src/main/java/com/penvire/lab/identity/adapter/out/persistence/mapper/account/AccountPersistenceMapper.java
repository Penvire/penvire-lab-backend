package com.penvire.lab.identity.adapter.out.persistence.mapper.account;

import com.penvire.lab.identity.adapter.out.persistence.entity.account.AccountEntity;
import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.Username;
import com.penvire.lab.persistence.EntityMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "jakarta-cdi")
public interface AccountPersistenceMapper extends EntityMapper<Account, AccountEntity> {

  @Override
  AccountEntity toEntity(Account domain);

  @Override
  Account toDomain(AccountEntity entity);

  @Override
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "email", source = "email")
  @Mapping(target = "username", source = "username")
  @Mapping(target = "enabled", source = "enabled")
  void updateEntity(Account domain, @MappingTarget AccountEntity entity);

  default String fromEmail(Email email) {
    return email.value();
  }

  default Email toEmail(String value) {
    return new Email(value);
  }

  default String fromUsername(Username username) {
    return username.value();
  }

  default Username toUsername(String value) {
    return new Username(value);
  }
}
