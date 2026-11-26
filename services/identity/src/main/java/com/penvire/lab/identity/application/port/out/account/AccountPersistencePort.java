package com.penvire.lab.identity.application.port.out.account;

import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.Username;
import java.util.Optional;
import java.util.UUID;

/** Stores Account data */
public interface AccountPersistencePort {

  /** Stores a new account */
  Account insert(Account account);

  Account update(Account account);

  Optional<Account> findById(UUID id);

  Optional<Account> findByUsername(Username username);

  Optional<Account> findByEmail(Email email);
}
