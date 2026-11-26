package com.penvire.lab.identity.domain.account;

import java.util.Locale;
import java.util.regex.Pattern;

public record Username(String value) {
  private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+$");

  public Username {
    value = value.strip().toLowerCase(Locale.ROOT);
    if (value.isBlank() || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid username");
    }
  }

  public static boolean validUsername(String username) {
    username = username.strip().toLowerCase(Locale.ROOT);
    return !(username.isBlank() || !PATTERN.matcher(username).matches());
  }
}
