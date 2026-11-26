package com.penvire.lab.identity.adapter.out.persistence.entity.account;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class AccountEntityRepository implements PanacheRepositoryBase<AccountEntity, UUID> {}
