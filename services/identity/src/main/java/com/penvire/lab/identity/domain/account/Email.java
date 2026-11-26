package com.penvire.lab.identity.domain.account;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {
  private static final Pattern PATTERN =
      Pattern.compile(
          "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"); // WHATWG HTML5 SPEC for emails

  public Email {
    value = value.strip().toLowerCase(Locale.ROOT);
    if (value.isBlank() || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid email address");
    }
  }

  public static boolean validEmail(String mail) {
    mail = mail.strip().toLowerCase(Locale.ROOT);
    return !(mail.isBlank() || !PATTERN.matcher(mail).matches());
  }
}
