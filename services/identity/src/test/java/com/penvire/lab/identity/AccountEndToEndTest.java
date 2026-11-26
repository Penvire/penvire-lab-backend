package com.penvire.lab.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.identity.adapter.out.persistence.entity.account.AccountEntityRepository;
import com.penvire.lab.identity.adapter.out.persistence.entity.account.PasswordCredentialEntity;
import com.penvire.lab.identity.adapter.out.persistence.entity.account.PasswordCredentialEntityRepository;
import com.penvire.lab.identity.application.port.in.account.AuthenticateAccountUseCase;
import com.penvire.lab.identity.application.port.in.account.ChangeAccountStatusUseCase;
import com.penvire.lab.identity.application.port.in.account.ChangePasswordUseCase;
import com.penvire.lab.identity.application.port.in.account.CreateAccountUseCase;
import com.penvire.lab.identity.application.port.in.account.GetAccountUseCase;
import com.penvire.lab.identity.domain.account.Account;
import com.penvire.lab.identity.domain.account.Email;
import com.penvire.lab.identity.domain.account.LoginIdentifier;
import com.penvire.lab.identity.domain.account.Username;
import dev.penvire.lab.identity.events.v1.AuditChange;
import dev.penvire.lab.identity.events.v1.AuditOutcome;
import dev.penvire.lab.identity.events.v1.AuditRecorded;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end account lifecycle through Quarkus CDI, transactions, real Argon2id hashing, audit
 * outbox recording, and PostgreSQL constraints.
 */
@QuarkusTest
class AccountEndToEndTest {

  private static final Email ALICE_EMAIL = new Email("alice@example.com");
  private static final Username ALICE_USERNAME = new Username("alice");
  private static final String PASSWORD_1 = "correct-horse-battery-staple";
  private static final String PASSWORD_2 = "tr0ub4dor-and-three-more";

  @Inject CreateAccountUseCase createAccount;
  @Inject AuthenticateAccountUseCase authenticateAccount;
  @Inject GetAccountUseCase getAccount;
  @Inject ChangePasswordUseCase changePassword;
  @Inject ChangeAccountStatusUseCase changeAccountStatus;
  @Inject AccountEntityRepository accountEntities;
  @Inject PasswordCredentialEntityRepository credentialEntities;
  @Inject RecordedIdentityAudits audits;

  @BeforeEach
  @AfterEach
  void emptyTables() {
    audits.clear();
    QuarkusTransaction.requiringNew()
        .run(() -> {
          credentialEntities.deleteAll();
          accountEntities.deleteAll();
        });
  }

  @Test
  void createsAccountStoresArgon2idHashAuthenticatesAndRecordsAudit() {
    Account created = createAccount.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertEquals(Optional.of(created), getAccount.getById(created.id()));

    List<PasswordCredentialEntity> stored =
        QuarkusTransaction.requiringNew().call(() -> credentialEntities.listAll());
    assertEquals(1, stored.size());
    assertTrue(stored.getFirst().getPasswordHash().startsWith("$argon2id$v=19$"));
    assertNotEquals(PASSWORD_1, stored.getFirst().getPasswordHash());

    assertEquals(
        Optional.of(created),
        authenticateAccount.authenticate(LoginIdentifier.parse("alice@example.com"), PASSWORD_1));
    assertEquals(
        Optional.of(created),
        authenticateAccount.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1));
    assertTrue(
        authenticateAccount
            .authenticate(LoginIdentifier.parse("alice"), "wrong-password-123")
            .isEmpty());

    List<RecordedIdentityAudits.Row> createdAudits = audits.ofAction("identity.account.create");
    assertEquals(1, createdAudits.size());
    AuditRecorded event = createdAudits.getFirst().event();
    assertEquals(AuditOutcome.SUCCESS, event.getOutcome());
    assertEquals("account", event.getTargetType().toString());
    assertEquals(created.id().toString(), event.getTargetId().toString());

    AuditChange enabledChange = requireChange(event, "enabled");
    assertEquals("true", enabledChange.getNewValue().toString());
    AuditChange emailChange = requireChange(event, "email.value");
    assertNull(emailChange.getOldValue());
    assertNull(emailChange.getNewValue());
    AuditChange usernameChange = requireChange(event, "username.value");
    assertNull(usernameChange.getOldValue());
    assertNull(usernameChange.getNewValue());
  }

  @Test
  void enforcesUniqueEmailAndUsername() {
    createAccount.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    assertThrows(
        IllegalArgumentException.class,
        () -> createAccount.create(ALICE_EMAIL, new Username("bob"), PASSWORD_1));
    assertThrows(
        IllegalArgumentException.class,
        () -> createAccount.create(new Email("bob@example.com"), ALICE_USERNAME, PASSWORD_1));

    long count = QuarkusTransaction.requiringNew().call(() -> accountEntities.count());
    assertEquals(1, count);
    assertEquals(1, audits.ofAction("identity.account.create").size());
  }

  @Test
  void disablingAccountPersistsBlocksAuthRecordsAuditUntilReEnabled() {
    Account created = createAccount.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    Account disabled = changeAccountStatus.disable(created.id());
    assertFalse(disabled.enabled());
    assertFalse(getAccount.getById(created.id()).orElseThrow().enabled());
    assertTrue(
        authenticateAccount.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1).isEmpty());

    AuditRecorded disableEvent = audits.ofAction("identity.account.disable").getFirst().event();
    assertEquals(AuditOutcome.SUCCESS, disableEvent.getOutcome());
    assertEquals(created.id().toString(), disableEvent.getTargetId().toString());
    AuditChange disableEnabled = requireChange(disableEvent, "enabled");
    assertEquals("true", disableEnabled.getOldValue().toString());
    assertEquals("false", disableEnabled.getNewValue().toString());

    Account enabled = changeAccountStatus.enable(created.id());
    assertTrue(enabled.enabled());
    assertTrue(getAccount.getById(created.id()).orElseThrow().enabled());
    assertEquals(
        Optional.of(enabled),
        authenticateAccount.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1));

    AuditRecorded enableEvent = audits.ofAction("identity.account.enable").getFirst().event();
    assertEquals(AuditOutcome.SUCCESS, enableEvent.getOutcome());
    assertEquals(created.id().toString(), enableEvent.getTargetId().toString());
    AuditChange enableEnabled = requireChange(enableEvent, "enabled");
    assertEquals("false", enableEnabled.getOldValue().toString());
    assertEquals("true", enableEnabled.getNewValue().toString());
  }

  @Test
  void changingPasswordRevokesPreviousRecordsAuditAndBlocksReuse() {
    Account created = createAccount.create(ALICE_EMAIL, ALICE_USERNAME, PASSWORD_1);

    changePassword.changePassword(created.id(), PASSWORD_1, PASSWORD_2);

    assertTrue(
        authenticateAccount.authenticate(LoginIdentifier.parse("alice"), PASSWORD_1).isEmpty());
    assertEquals(
        Optional.of(created),
        authenticateAccount.authenticate(LoginIdentifier.parse("alice"), PASSWORD_2));

    List<PasswordCredentialEntity> stored =
        QuarkusTransaction.requiringNew()
            .call(() ->
                credentialEntities
                    .find("accountId = ?1 order by createdAt desc", created.id())
                    .list());
    assertEquals(2, stored.size());
    assertEquals(1, stored.stream().filter(c -> c.getRevokedAt() == null).count());
    assertEquals(1, stored.stream().filter(c -> c.getRevokedAt() != null).count());

    List<RecordedIdentityAudits.Row> passwordAudits =
        audits.ofAction("identity.account.change-password");
    assertEquals(1, passwordAudits.size());
    AuditRecorded passwordEvent = passwordAudits.getFirst().event();
    assertEquals(AuditOutcome.SUCCESS, passwordEvent.getOutcome());
    assertEquals("account", passwordEvent.getTargetType().toString());
    assertEquals(created.id().toString(), passwordEvent.getTargetId().toString());
    assertTrue(passwordEvent.getChanges().isEmpty());

    assertThrows(
        IllegalArgumentException.class,
        () -> changePassword.changePassword(created.id(), PASSWORD_2, PASSWORD_1));
  }

  private static AuditChange requireChange(AuditRecorded event, String path) {
    AuditChange found =
        event.getChanges().stream()
            .filter(c -> c.getFieldPath().toString().equals(path))
            .findFirst()
            .orElse(null);
    assertNotNull(found, () -> "Missing audit change for path " + path);
    return found;
  }
}
