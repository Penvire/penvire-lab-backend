package com.penvire.lab.identity.application.port.in.account;

import com.penvire.lab.identity.domain.account.Account;
import java.util.Optional;
import java.util.UUID;

public interface GetAccountUseCase {
  Optional<Account> getById(UUID accountId);
}
