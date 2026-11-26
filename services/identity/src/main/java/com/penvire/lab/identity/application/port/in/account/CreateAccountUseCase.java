package com.penvire.lab.identity.application.port.in.account;

import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.Username;

public interface CreateAccountUseCase {
  Account create(Email email, Username username, String rawPassword);
}
