package com.penvire.lab.event;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.avro.Schema;

/**
 * The CloudEvents {@code type} and {@code source} of an event, derived from its Avro full name
 * {@code dev.penvire.lab.<service>.events.v<major>.<RecordName>} (ADR 0004). Contracts CI checks
 * that each AsyncAPI {@code ce_type} follows the same rule, so the two cannot drift apart.
 */
public record EventType(String service, String event, int major) {

  private static final Pattern NAMESPACE =
      Pattern.compile("dev\\.penvire\\.lab\\.([a-z][a-z0-9-]*)\\.events\\.v([1-9][0-9]*)");

  /**
   * Derives the event type from a generated schema.
   *
   * @throws IllegalArgumentException if the schema is not in a Penvire Lab events namespace
   */
  public static EventType of(Schema schema) {
    Matcher matcher = NAMESPACE.matcher(schema.getNamespace());
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          schema.getFullName() + " is not in dev.penvire.lab.<service>.events.v<major>");
    }

    return new EventType(
        matcher.group(1), kebab(schema.getName()), Integer.parseInt(matcher.group(2)));
  }

  /**
   * Whether the namespace holds Penvire Lab events. Generated classes live in the Java package of
   * their schema's namespace, so this also tells whether a class is a generated event class.
   */
  public static boolean isEventNamespace(String namespace) {
    return NAMESPACE.matcher(namespace).matches();
  }

  /** For example {@code dev.penvire.lab.identity.session-started.v1} */
  public String type() {
    return "dev.penvire.lab." + service + "." + event + ".v" + major;
  }

  /** For example {@code /penvire-lab/identity}. */
  public String source() {
    return "/penvire-lab/" + service;
  }

  /** The same rule as {@code kebab} in the contracts repository's generator/asyncapi/rules.js. */
  static String kebab(String recordName) {
    return recordName
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
        .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
        .toLowerCase(Locale.ROOT);
  }
}
