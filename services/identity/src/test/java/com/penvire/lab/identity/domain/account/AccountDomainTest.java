package com.penvire.lab.identity.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountDomainTest {

  private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");

  @Test
  void emailNormalizesWhitespaceAndCase() {
    Email email = new Email("  Alice.Test+1@Example.COM  ");

    assertEquals("alice.test+1@example.com", email.value());
    assertTrue(Email.validEmail("  Alice@Example.COM "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "alice", "alice@", "@example.com", "alice@localhost"})
  void emailRejectsInvalidValues(String raw) {
    assertFalse(Email.validEmail(raw));
    assertThrows(IllegalArgumentException.class, () -> new Email(raw));
  }

  @Test
  void usernameNormalizesWhitespaceAndCase() {
    Username username = new Username("  Alice42  ");

    assertEquals("alice42", username.value());
    assertTrue(Username.validUsername("  Bob99 "));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "alice_bob", "alice-bob", "alice@example.com", "alice bob"})
  void usernameRejectsInvalidValues(String raw) {
    assertFalse(Username.validUsername(raw));
    assertThrows(IllegalArgumentException.class, () -> new Username(raw));
  }

  @Test
  void loginIdentifierParsesEmailOrUsernameAndRejectsInvalid() {
    assertEquals(
        new LoginIdentifier.ByEmail(new Email("alice@example.com")),
        LoginIdentifier.parse("  ALICE@Example.com "));
    assertEquals(
        new LoginIdentifier.ByUsername(new Username("alice42")),
        LoginIdentifier.parse("  Alice42 "));
    assertThrows(IllegalArgumentException.class, () -> LoginIdentifier.parse("not valid!"));
  }

  @Test
  void passwordHashValidatesAndRedactsToString() {
    PasswordHash hash = new PasswordHash("12345678");

    assertEquals("12345678", hash.value());
    assertEquals("PasswordHash[REDACTED]", hash.toString());
    assertThrows(IllegalArgumentException.class, () -> new PasswordHash(""));
    assertThrows(IllegalArgumentException.class, () -> new PasswordHash("        "));
  }

  @Test
  void passwordCredentialLifecycleAndInvariants() {
    UUID id = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    PasswordHash hash = new PasswordHash("$argon2id$v=19$hash");

    PasswordCredential active = PasswordCredential.active(id, accountId, hash, T0);
    assertTrue(active.isActive());
    assertNull(active.revokedAt());

    PasswordCredential revoked = active.revoke(T0.plusSeconds(10));
    assertFalse(revoked.isActive());
    assertEquals(T0.plusSeconds(10), revoked.revokedAt());
    assertThrows(IllegalStateException.class, () -> revoked.revoke(T0.plusSeconds(20)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PasswordCredential(id, accountId, hash, T0, T0.minusSeconds(1)));
  }

  @Test
  void accountSubjectAndEnableDisableTransitions() {
    UUID id = UUID.randomUUID();
    Account enabled =
        new Account(id, new Email("alice@example.com"), new Username("alice"), true, T0);

    assertEquals(id.toString(), enabled.subject());
    Account disabled = enabled.disable();
    assertFalse(disabled.enabled());
    assertEquals(enabled.id(), disabled.id());
    assertEquals(enabled.email(), disabled.email());
    assertEquals(enabled.username(), disabled.username());
    assertEquals(enabled.createdAt(), disabled.createdAt());
    assertEquals(enabled, disabled.enable());
  }
}
