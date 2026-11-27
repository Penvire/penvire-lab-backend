package com.penvire.lab.identity.adapter.out.persistence.adapter.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.adapter.out.persistence.entity.account.AccountEntityRepository;
import com.penvire.lab.identity.adapter.out.persistence.entity.account.PasswordCredentialEntityRepository;
import com.penvire.lab.identity.application.port.out.account.AccountPersistencePort;
import com.penvire.lab.identity.application.port.out.account.PasswordCredentialPersistencePort;
import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.PasswordCredential;
import com.penvire.lab.identity.domain.account.PasswordHash;
import com.penvire.lab.identity.domain.account.Username;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AccountPersistenceTest {

  private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");

  @Inject AccountPersistencePort accounts;
  @Inject PasswordCredentialPersistencePort credentials;
  @Inject AccountEntityRepository accountEntities;
  @Inject PasswordCredentialEntityRepository credentialEntities;

  @BeforeEach
  @AfterEach
  void emptyTables() {
    QuarkusTransaction.requiringNew()
        .run(() -> {
          credentialEntities.deleteAll();
          accountEntities.deleteAll();
        });
  }

  @Test
  void insertsFindsAndUpdatesAccount() {
    Account alice =
        new Account(
            UUID.randomUUID(), new Email("alice@example.com"), new Username("alice"), true, T0);

    QuarkusTransaction.requiringNew().run(() -> accounts.insert(alice));

    assertEquals(
        Optional.of(alice),
        QuarkusTransaction.requiringNew().call(() -> accounts.findById(alice.id())));
    assertEquals(
        Optional.of(alice),
        QuarkusTransaction.requiringNew().call(() -> accounts.findByEmail(alice.email())));
    assertEquals(
        Optional.of(alice),
        QuarkusTransaction.requiringNew().call(() -> accounts.findByUsername(alice.username())));

    Account disabled = alice.disable();
    QuarkusTransaction.requiringNew().run(() -> accounts.update(disabled));

    assertEquals(
        Optional.of(disabled),
        QuarkusTransaction.requiringNew().call(() -> accounts.findById(alice.id())));
  }

  @Test
  void databaseRejectsDuplicateEmailOrUsername() {
    Account alice =
        new Account(
            UUID.randomUUID(), new Email("alice@example.com"), new Username("alice"), true, T0);
    QuarkusTransaction.requiringNew().run(() -> accounts.insert(alice));

    Account sameEmail =
        new Account(
            UUID.randomUUID(), new Email("alice@example.com"), new Username("bob"), true, T0);
    assertThrows(
        PersistenceException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(() -> {
                  accounts.insert(sameEmail);
                  accountEntities.flush();
                }));

    Account sameUsername =
        new Account(
            UUID.randomUUID(), new Email("bob@example.com"), new Username("alice"), true, T0);
    assertThrows(
        PersistenceException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(() -> {
                  accounts.insert(sameUsername);
                  accountEntities.flush();
                }));
  }

  @Test
  void databaseEnforcesAtMostOneActivePasswordPerAccount() {
    Account alice = insertAccount("alice@example.com", "alice");
    PasswordCredential first =
        PasswordCredential.active(
            UUID.randomUUID(), alice.id(), new PasswordHash("hash-one-123"), T0);
    PasswordCredential second =
        PasswordCredential.active(
            UUID.randomUUID(), alice.id(), new PasswordHash("hash-two-123"), T0.plusSeconds(1));

    QuarkusTransaction.requiringNew().run(() -> credentials.insert(first));

    assertThrows(
        PersistenceException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(() -> {
                  credentials.insert(second);
                  credentialEntities.flush();
                }));
  }

  @Test
  void revokingThenInsertingInSameTransactionUpdatesActiveAndRecentReturnsNewestFirst() {
    Account alice = insertAccount("alice@example.com", "alice");
    PasswordCredential c1 =
        PasswordCredential.active(UUID.randomUUID(), alice.id(), new PasswordHash("hash-0001"), T0);
    PasswordCredential c2 =
        PasswordCredential.active(
            UUID.randomUUID(), alice.id(), new PasswordHash("hash-0002"), T0.plusSeconds(10));
    PasswordCredential c3 =
        PasswordCredential.active(
            UUID.randomUUID(), alice.id(), new PasswordHash("hash-0003"), T0.plusSeconds(20));

    QuarkusTransaction.requiringNew().run(() -> credentials.insert(c1));
    QuarkusTransaction.requiringNew()
        .run(() -> {
          credentials.update(c1.revoke(T0.plusSeconds(10)));
          credentials.insert(c2);
        });
    QuarkusTransaction.requiringNew()
        .run(() -> {
          credentials.update(c2.revoke(T0.plusSeconds(20)));
          credentials.insert(c3);
        });

    Optional<PasswordCredential> active =
        QuarkusTransaction.requiringNew().call(() -> credentials.findActiveByAccountId(alice.id()));
    assertEquals(Optional.of(c3), active);

    List<PasswordCredential> topTwo =
        QuarkusTransaction.requiringNew()
            .call(() -> credentials.findRecentByAccountId(alice.id(), 2));
    assertEquals(2, topTwo.size());
    assertEquals(c3.id(), topTwo.get(0).id());
    assertTrue(topTwo.get(0).isActive());
    assertEquals(c2.id(), topTwo.get(1).id());
    assertFalse(topTwo.get(1).isActive());
    assertEquals(T0.plusSeconds(20), topTwo.get(1).revokedAt());
  }

  @Test
  void updateThrowsWhenCredentialDoesNotExist() {
    Account alice = insertAccount("alice@example.com", "alice");
    PasswordCredential missing =
        PasswordCredential.active(UUID.randomUUID(), alice.id(), new PasswordHash("hash-0001"), T0);

    assertThrows(
        IllegalStateException.class,
        () -> QuarkusTransaction.requiringNew().run(() -> credentials.update(missing)));
  }

  private Account insertAccount(String email, String username) {
    Account account =
        new Account(UUID.randomUUID(), new Email(email), new Username(username), true, T0);
    QuarkusTransaction.requiringNew().run(() -> accounts.insert(account));
    return account;
  }
}
