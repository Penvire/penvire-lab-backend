package com.penvire.lab.identity.domain.account;

import java.util.Locale;

public sealed interface LoginIdentifier
    permits LoginIdentifier.ByEmail, LoginIdentifier.ByUsername {
  record ByEmail(Email email) implements LoginIdentifier {}

  record ByUsername(Username username) implements LoginIdentifier {}

  static LoginIdentifier parse(String raw) {
    String trimmed = raw.strip().toLowerCase(Locale.ROOT);

    if (Email.validEmail(trimmed)) {
      return new ByEmail(new Email(trimmed));
    } else if (Username.validUsername(trimmed)) {
      return new ByUsername(new Username(trimmed));
    } else {
      throw new IllegalArgumentException("Invalid login identifier");
    }
  }
}
