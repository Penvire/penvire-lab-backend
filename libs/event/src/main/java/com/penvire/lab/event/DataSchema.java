package com.penvire.lab.event;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CloudEvents {@code dataschema} attribute: which revision of which schema an event was written
 * with, as {@code urn:penvire-lab:schema:<full name>:<revision>} (ADR 0004).
 */
public record DataSchema(String fullName, int revision) {
  private static final String PREFIX = "urn:penvire-lab:schema:";
  private static final Pattern URN =
      Pattern.compile(
          Pattern.quote(PREFIX)
              + "([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*):([1-9][0-9]*)");

  /**
   * Reads the attribute of an incoming event.
   *
   * @throws IllegalArgumentException if it is not a Penvire Lab schema URN
   */
  public static DataSchema parse(String urn) {
    Matcher matcher = URN.matcher(urn);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Not a Penvire Lab schema URN: " + urn);
    }
    return new DataSchema(matcher.group(1), Integer.parseInt(matcher.group(2)));
  }

  public String urn() {
    return PREFIX + fullName + ":" + revision;
  }
}
