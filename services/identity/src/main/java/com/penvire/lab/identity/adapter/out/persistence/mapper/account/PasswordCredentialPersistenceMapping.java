package com.penvire.lab.identity.adapter.out.persistence.mapper.account;

import com.penvire.lab.identity.adapter.out.persistence.entity.account.PasswordCredentialEntity;
import com.penvire.lab.identity.domain.account.PasswordCredential;
import com.penvire.lab.identity.domain.account.PasswordHash;
import com.penvire.lab.persistence.EntityMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "jakarta-cdi")
public interface PasswordCredentialPersistenceMapping
    extends EntityMapper<PasswordCredential, PasswordCredentialEntity> {

  @Override
  PasswordCredentialEntity toEntity(PasswordCredential domain);

  @Override
  PasswordCredential toDomain(PasswordCredentialEntity entity);

  @Override
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "revokedAt", source = "revokedAt")
  void updateEntity(PasswordCredential domain, @MappingTarget PasswordCredentialEntity entity);

  default String fromPasswordHash(PasswordHash passwordHash) {
    return passwordHash.value();
  }

  default PasswordHash toPasswordHash(String passwordHash) {
    return new PasswordHash(passwordHash);
  }
}
