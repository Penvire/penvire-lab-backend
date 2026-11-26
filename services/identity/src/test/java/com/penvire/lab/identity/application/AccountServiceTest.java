package com.penvire.lab.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.audit.TestAuditScope;
import com.penvire.lab.clock.MutableClock;
import com.penvire.lab.identity.application.port.out.account.AccountPersistencePort;
import com.penvire.lab.identity.application.port.out.account.PasswordCredentialPersistencePort;
import com.penvire.lab.identity.application.port.out.account.PasswordHasherPort;
import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.LoginIdentifier;
import com.penvire.lab.identity.domain.account.PasswordCredential;
import com.penvire.lab.identity.domain.account.PasswordHash;
import com.penvire.lab.identity.domain.account.Username;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceTest {

  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final Email ALICE_EMAIL = new Email("alice@example.com");
  private static final Username ALICE_USERNAME = new Username("alice");
  private static final String PASSWORD_1 = "correct-horse-battery-staple";
  private static final String PASSWORD_2 = "tr0ub4dor-and-three-more";

  private final MutableClock clock = MutableClock.at(NOW);
  private final InMemoryAccounts accounts = new InMemoryAccounts();
  private final InMemoryPasswordCredentials credentials = new InMemoryPasswordCredentials();
  private final CountingFakeHasher hasher = new CountingFakeHasher();
  private final AccountService service =
      new AccountService(
          UUID::randomUUID, accounts, credentials, hasher, clock, new TestAuditScope());

  @Test
  void createsEnabledAccountWithActiveHashedCredential() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertEquals(ALICE_EMAIL, created.email());
    assertEquals(ALICE_USERNAME, created.username());
    assertTrue(created.enabled());
    assertEquals(NOW, created.createdAt());

    Optional<PasswordCredential> active = credentials.findActiveByAccountId(created.id());
    assertTrue(active.isPresent());
    assertTrue(hasher.verify(PASSWORD_1, active.get().passwordHash()));
  }

  @Test
  void rejectsDuplicateEmailOrUsername() {
    service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(ALICE_EMAIL, new Username("bob"), PASSWORD_1));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(new Email("bob@example.com"), ALICE_USERNAME, PASSWORD_1));
  }

  @Test
  void rejectsPasswordsOutsideLengthBounds() {
    assertThrows(
        IllegalArgumentException.class, () -> service.create(ALICE_EMAIL, ALICE_USERNAME, "short"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(ALICE_EMAIL, ALICE_USERNAME, "a".repeat(129)));
  }

  @Test
  void getsAccountByIdOrEmptyWhenMissing() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertEquals(Optional.of(created), service.getById(created.id()));
    assertTrue(service.getById(UUID.randomUUID()).isEmpty());
  }

  @Test
  void authenticatesByEmailOrUsername() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertEquals(
        Optional.of(created),
        service.authenticate(LoginIdentifier.parse("alice@example.com"), PASSWORD_1));
    assertEquals(
        Optional.of(created), service.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1));
  }

  @Test
  void rejectsWrongPassword() {
    service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertTrue(
        service.authenticate(LoginIdentifier.parse("alice"), "wrong-password-123").isEmpty());
  }

  @Test
  void rejectsMissingOrDisabledAccountWithTimingEqualization() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    int beforeMissing = hasher.verifyCalls;
    assertTrue(service.authenticate(LoginIdentifier.parse("nobody"), PASSWORD_1).isEmpty());
    assertEquals(beforeMissing + 1, hasher.verifyCalls);

    service.disable(created.id());
    int beforeDisabled = hasher.verifyCalls;
    assertTrue(service.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1).isEmpty());
    assertEquals(beforeDisabled + 1, hasher.verifyCalls);
  }

  @Test
  void disablesAndReEnablesAccount() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    Account disabled = service.disable(created.id());
    assertFalse(disabled.enabled());
    assertFalse(service.getById(created.id()).orElseThrow().enabled());

    Account enabled = service.enable(created.id());
    assertTrue(enabled.enabled());
    assertTrue(service.getById(created.id()).orElseThrow().enabled());
  }

  @Test
  void statusChangeRejectsUnknownAccount() {
    UUID unknown = UUID.randomUUID();

    assertThrows(IllegalArgumentException.class, () -> service.disable(unknown));
    assertThrows(IllegalArgumentException.class, () -> service.enable(unknown));
  }

  @Test
  void changesPasswordRevokesPreviousAndBlocksReuse() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);
    clock.advance(Duration.ofMinutes(5));

    service.changePassword(created.id(), PASSWORD_1, PASSWORD_2);

    assertTrue(service.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1).isEmpty());
    assertEquals(
        Optional.of(created), service.authenticate(LoginIdentifier.parse("alice"), PASSWORD_2));

    List<PasswordCredential> recent = credentials.findRecentByAccountId(created.id(), 5);
    assertEquals(2, recent.size());
    assertTrue(recent.get(0).isActive());
    assertFalse(recent.get(1).isActive());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.changePassword(created.id(), PASSWORD_2, PASSWORD_1));
  }

  @Test
  void changePasswordRejectsWrongCurrentPasswordDisabledOrUnknownAccount() {
    Account created = service.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.changePassword(created.id(), "wrong-current-password", PASSWORD_2));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.changePassword(created.id(), PASSWORD_1, "short"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.changePassword(UUID.randomUUID(), PASSWORD_1, PASSWORD_2));

    service.disable(created.id());
    assertThrows(
        IllegalStateException.class,
        () -> service.changePassword(created.id(), PASSWORD_1, PASSWORD_2));
  }

  private static final class InMemoryAccounts implements AccountPersistencePort {
    private final Map<UUID, Account> byId = new LinkedHashMap<>();

    @Override
    public Account insert(Account account) {
      byId.put(account.id(), account);
      return account;
    }

    @Override
    public Account update(Account account) {
      byId.put(account.id(), account);
      return account;
    }

    @Override
    public Optional<Account> findById(UUID id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Account> findByUsername(Username username) {
      return byId.values().stream().filter(a -> a.username().equals(username)).findFirst();
    }

    @Override
    public Optional<Account> findByEmail(Email email) {
      return byId.values().stream().filter(a -> a.email().equals(email)).findFirst();
    }
  }

  private static final class InMemoryPasswordCredentials
      implements PasswordCredentialPersistencePort {
    private final Map<UUID, PasswordCredential> byId = new LinkedHashMap<>();

    @Override
    public PasswordCredential insert(PasswordCredential credential) {
      byId.put(credential.id(), credential);
      return credential;
    }

    @Override
    public void changePassword(UUID accountId, PasswordCredential newCredential, Instant now) {
      findActiveByAccountId(accountId)
          .ifPresent(active -> byId.put(active.id(), active.revoke(now)));
      byId.put(newCredential.id(), newCredential);
    }

    @Override
    public Optional<PasswordCredential> findActiveByAccountId(UUID accountId) {
      return byId.values().stream()
          .filter(c -> c.accountId().equals(accountId) && c.isActive())
          .findFirst();
    }

    @Override
    public List<PasswordCredential> findRecentByAccountId(UUID accountId, int limit) {
      List<PasswordCredential> matching = new ArrayList<>();
      for (PasswordCredential c : byId.values()) {
        if (c.accountId().equals(accountId)) {
          matching.add(c);
        }
      }
      matching.sort(Comparator.comparing(PasswordCredential::createdAt).reversed());
      return matching.stream().limit(limit).toList();
    }
  }

  private static final class CountingFakeHasher implements PasswordHasherPort {
    int verifyCalls;

    @Override
    public PasswordHash hash(String password) {
      return new PasswordHash("fake-hash:" + password);
    }

    @Override
    public boolean verify(String password, PasswordHash hash) {
      verifyCalls++;
      return hash.value().equals("fake-hash:" + password);
    }
  }
}
