package com.penvire.lab.fga.model;

/**
 * Names an OpenFGA relation or permission (for example {@code admin} or {@code can_view}). Services
 * implement this on permission enums so relation names are checked at compile time.
 */
@FunctionalInterface
public interface FgaRelation {

  /** Returns the lowercase OpenFGA relation name. */
  String fgaRelation();

  /**
   * Wraps a validated relation name, such as one selected dynamically for a custom role.
   *
   * @throws IllegalArgumentException if {@code relation} is not a valid OpenFGA identifier
   */
  static FgaRelation of(String relation) {
    String validated = FgaValidation.requireRelation(relation);
    return () -> validated;
  }
}
