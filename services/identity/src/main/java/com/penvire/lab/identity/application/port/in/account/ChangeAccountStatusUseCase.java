package com.penvire.lab.identity.application.port.in.account;

import com.penvire.lab.identity.domain.account.Account;
import java.util.UUID;

public interface ChangeAccountStatusUseCase {
  Account enable(UUID accountId);

  Account disable(UUID accountId);
}
