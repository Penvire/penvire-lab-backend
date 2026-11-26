package com.penvire.lab.identity.application.port.in.account;

import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.LoginIdentifier;
import java.util.Optional;

public interface AuthenticateAccountUseCase {
  /** Returns the account if it exists, is enabled, and the password matches. */
  Optional<Account> authenticate(LoginIdentifier identifier, String rawPassword);
}
