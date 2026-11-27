package com.penvire.lab.problem;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a client learns about one occurrence of a {@link ProblemType}, beyond its type and title
 * (RFC 9457). Everything here is shown to the client: no internal hosts, statuses or identifiers
 * the caller may not see. Start from {@link #NONE} and add what applies.
 *
 * @param detail explains this occurrence to a person
 * @param properties facts a client can act on, rendered as extension members; values must serialize
 *     to JSON
 * @param retryAfter how long to wait before retrying, meant for {@link ProblemKind#UNAVAILABLE}
 */
public record ProblemOccurrence(
    @Nullable String detail, Map<String, Object> properties, @Nullable Duration retryAfter) {

  public static final ProblemOccurrence NONE = new ProblemOccurrence(null, Map.of(), null);

  private static final Pattern PROPERTY_NAME = Pattern.compile("[a-z][a-zA-Z0-9]*");

  /** The standard members of RFC 9457, and the trace id the problem library adds itself. */
  private static final Set<String> RESERVED_NAMES =
      Set.of("type", "title", "status", "detail", "instance", "traceId");

  public ProblemOccurrence {
    detail = detail == null || detail.isBlank() ? null : detail.strip();

    properties = Map.copyOf(properties);
    for (var name : properties.keySet()) {
      if (!PROPERTY_NAME.matcher(name).matches() || RESERVED_NAMES.contains(name)) {
        throw new IllegalArgumentException(
            "Problem property name must match " + PROPERTY_NAME + " and not be reserved:" + name);
      }
    }

    if (retryAfter != null && retryAfter.isNegative()) {
      throw new IllegalArgumentException("Problem retryAfter must be positive: " + retryAfter);
    }
  }

  public ProblemOccurrence withDetail(String detail) {
    return new ProblemOccurrence(detail, properties, retryAfter);
  }

  /** Adds an extension member, replacing an earlier value of the same name. */
  public ProblemOccurrence withProperty(String name, Object value) {
    var merged = new HashMap<>(properties);
    merged.put(name, value);
    return new ProblemOccurrence(detail, merged, retryAfter);
  }

  public ProblemOccurrence withRetryAfter(Duration retryAfter) {
    return new ProblemOccurrence(detail, properties, retryAfter);
  }
}
