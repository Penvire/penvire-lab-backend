package com.penvire.lab.fga.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates OpenFGA identifiers and object or subject IDs before they are formatted into tuple
 * strings ({@code type:id} or {@code type:id#relation}). Rejecting reserved characters ({@code :},
 * {@code #}, {@code *}, whitespace) at construction time prevents tuple-string injection.
 */
public final class FgaValidation {

  private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]*$");
  private static final Pattern ID = Pattern.compile("^[^:#\\s*]+$");

  private FgaValidation() {}

  /**
   * Validates an OpenFGA type name (lowercase snake_case).
   *
   * @throws IllegalArgumentException if {@code type} is blank or not a valid identifier
   */
  public static String requireType(String type) {
    return requireMatch(type, IDENTIFIER, "type");
  }

  /**
   * Validates an OpenFGA relation or permission name (lowercase snake_case).
   *
   * @throws IllegalArgumentException if {@code relation} is blank or not a valid identifier
   */
  public static String requireRelation(String relation) {
    return requireMatch(relation, IDENTIFIER, "relation");
  }

  /**
   * Validates an OpenFGA object or subject ID. Forbids {@code :}, {@code #}, {@code *}, and
   * whitespace so untrusted IDs cannot inject usersets, wildcards, or extra delimiters.
   *
   * @throws IllegalArgumentException if {@code id} is blank or contains forbidden characters
   */
  public static String requireId(String id) {
    return requireMatch(id, ID, "id");
  }

  private static String requireMatch(String value, Pattern pattern, String label) {
    Objects.requireNonNull(value, label + " must not be null");
    if (!pattern.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid FGA " + label + ": " + value);
    }
    return value;
  }
}
