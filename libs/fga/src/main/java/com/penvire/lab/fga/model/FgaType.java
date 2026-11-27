package com.penvire.lab.fga.model;

/**
 * Names an OpenFGA object or subject type (for example {@code tenant} or {@code engagement}).
 * Services implement this on constants or enums to avoid raw type strings at call sites.
 */
@FunctionalInterface
public interface FgaType {

  /** Returns the lowercase OpenFGA type name. */
  String fgaType();

  /**
   * Wraps a validated type name for dynamic or ad-hoc lookups.
   *
   * @throws IllegalArgumentException if {@code type} is not a valid OpenFGA identifier
   */
  static FgaType of(String type) {
    String validated = FgaValidation.requireType(type);
    return () -> validated;
  }
}
