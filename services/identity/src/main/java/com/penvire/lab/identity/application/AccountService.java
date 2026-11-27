package com.penvire.lab.identity.application;

import com.penvire.lab.audit.AuditScope;
import com.penvire.lab.audit.Audited;
import com.penvire.lab.identity.application.port.in.account.AuthenticateAccountUseCase;
import com.penvire.lab.identity.application.port.in.account.ChangeAccountStatusUseCase;
import com.penvire.lab.identity.application.port.in.account.ChangePasswordUseCase;
import com.penvire.lab.identity.application.port.in.account.CreateAccountUseCase;
import com.penvire.lab.identity.application.port.in.account.GetAccountUseCase;
import com.penvire.lab.identity.application.port.out.account.AccountPersistencePort;
import com.penvire.lab.identity.application.port.out.account.PasswordCredentialPersistencePort;
import com.penvire.lab.identity.application.port.out.account.PasswordHasherPort;
import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.LoginIdentifier;
import com.penvire.lab.identity.domain.account.PasswordCredential;
import com.penvire.lab.identity.domain.account.PasswordHash;
import com.penvire.lab.identity.domain.account.Username;
import com.penvire.lab.persistence.IdGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AccountService
    implements CreateAccountUseCase,
        AuthenticateAccountUseCase,
        GetAccountUseCase,
        ChangePasswordUseCase,
        ChangeAccountStatusUseCase {

  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_PASSWORD_LENGTH = 128;
  private static final int PASSWORD_REUSE_LIMIT = 5;

  private final IdGenerator idGenerator;
  private final AccountPersistencePort accounts;
  private final PasswordCredentialPersistencePort passwordCredentials;
  private final PasswordHasherPort passwordHasher;
  private final Clock clock;
  private final AuditScope audit;

  private PasswordHash dummyHash;

  public AccountService(
      IdGenerator idGenerator,
      AccountPersistencePort accounts,
      PasswordCredentialPersistencePort passwordCredentials,
      PasswordHasherPort passwordHasher,
      Clock clock,
      AuditScope audit) {
    this.idGenerator = idGenerator;
    this.accounts = accounts;
    this.passwordCredentials = passwordCredentials;
    this.passwordHasher = passwordHasher;
    this.clock = clock;
    this.audit = audit;

    this.dummyHash = passwordHasher.hash("DummyHash");
  }

  @Override
  @Transactional
  public Optional<Account> authenticate(LoginIdentifier identifier, String rawPassword) {
    Optional<Account> account =
        switch (identifier) {
          case LoginIdentifier.ByEmail email -> accounts.findByEmail(email.email());
          case LoginIdentifier.ByUsername username -> accounts.findByUsername(username.username());
        };

    if (account.isEmpty() || !account.get().enabled()) {
      passwordHasher.verify(rawPassword, dummyHash);
      return Optional.empty();
    }

    var credential = passwordCredentials.findActiveByAccountId(account.get().id());
    if (credential.isPresent()) {
      if (!passwordHasher.verify(rawPassword, credential.get().passwordHash())) {
        return Optional.empty();
      }
    } else {
      passwordHasher.verify(rawPassword, dummyHash);
      return Optional.empty();
    }

    return account;
  }

  @Override
  @Transactional
  @Audited(value = "identity.account.change-password")
  public void changePassword(UUID accountId, String currentPassword, String newPassword) {
    Instant now = clock.instant();
    validatePassword(newPassword);

    Account account =
        getById(accountId)
            .orElseThrow(
                () -> new IllegalArgumentException("Account not found")); // Check if account exists
    if (!account.enabled()) {
      throw new IllegalStateException("Account is disabled");
    }

    PasswordCredential active =
        passwordCredentials
            .findActiveByAccountId(accountId)
            .orElseThrow(
                () -> new IllegalStateException("No active password credential for account"));
    if (!passwordHasher.verify(currentPassword, active.passwordHash())) {
      throw new IllegalArgumentException("Invalid password");
    }

    var lastCredentials =
        passwordCredentials.findRecentByAccountId(accountId, PASSWORD_REUSE_LIMIT);
    for (var oldCred : lastCredentials) {
      if (passwordHasher.verify(newPassword, oldCred.passwordHash())) {
        throw new IllegalArgumentException(
            "New password cannot be the same as any of the last "
                + PASSWORD_REUSE_LIMIT
                + " passwords");
      }
    }

    PasswordCredential newCredential =
        PasswordCredential.active(
            idGenerator.next(), accountId, passwordHasher.hash(newPassword), now);
    passwordCredentials.update(active.revoke(now));
    passwordCredentials.insert(newCredential);

    audit.target(accountId.toString());
  }

  @Override
  @Transactional
  @Audited(
      value = "identity.account.create",
      state = Account.class,
      pathOnly = {"email", "username"})
  public Account create(Email email, Username username, String rawPassword) {
    Instant now = clock.instant();

    validatePassword(rawPassword);
    if (accounts.findByEmail(email).isPresent()) {
      throw new IllegalArgumentException("Email already in use");
    }

    if (accounts.findByUsername(username).isPresent()) {
      throw new IllegalArgumentException("Username already in use");
    }

    Account account = new Account(idGenerator.next(), email, username, true, now);
    PasswordHash hash = passwordHasher.hash(rawPassword);
    PasswordCredential credential =
        PasswordCredential.active(idGenerator.next(), account.id(), hash, now);

    account = accounts.insert(account);
    passwordCredentials.insert(credential);

    audit.target(account.id().toString());
    audit.state(null, account);

    return account;
  }

  @Override
  @Transactional
  public Optional<Account> getById(UUID accountId) {
    return accounts.findById(accountId);
  }

  @Override
  @Transactional
  @Audited(
      value = "identity.account.enable",
      state = Account.class,
      pathOnly = {"email", "username"})
  public Account enable(UUID accountId) {
    Account before =
        accounts
            .findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException(accountId.toString()));

    Account account = before.enable();
    account = accounts.update(account);

    audit.target(account.id().toString());
    audit.state(before, account);

    return account;
  }

  @Override
  @Transactional
  @Audited(
      value = "identity.account.disable",
      state = Account.class,
      pathOnly = {"email", "username"})
  public Account disable(UUID accountId) {
    Account before =
        accounts
            .findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException(accountId.toString()));

    Account account = before.disable();
    account = accounts.update(account);

    audit.target(account.id().toString());
    audit.state(before, account);

    return account;
  }

  private void validatePassword(String rawPassword) {
    if (rawPassword.isEmpty()) {
      throw new IllegalArgumentException("Password cannot be empty");
    }

    if (rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.length() > MAX_PASSWORD_LENGTH) {
      throw new IllegalArgumentException(
          "Password must be between "
              + MIN_PASSWORD_LENGTH
              + " and "
              + MAX_PASSWORD_LENGTH
              + " characters");
    }
  }
}
