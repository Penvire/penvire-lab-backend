package com.penvire.lab.fga.model;

import java.util.UUID;

/**
 * Validated OpenFGA object reference formatted as {@code <type>:<id>} (for example {@code
 * tenant:0196...}). Both {@code type} and {@code id} are validated on construction to prevent
 * tuple-string injection.
 */
public record FgaObject(String type, String id) implements FgaResource {

  /**
   * Creates a validated OpenFGA object reference.
   *
   * @throws IllegalArgumentException if {@code type} or {@code id} violates OpenFGA syntax
   */
  public FgaObject {
    FgaValidation.requireType(type);
    FgaValidation.requireId(id);
  }

  /** Creates an object reference from a typed {@link FgaType} and ID string. */
  public static FgaObject of(FgaType type, String id) {
    return new FgaObject(type.fgaType(), id);
  }

  /** Creates an object reference from a typed {@link FgaType} and UUID. */
  public static FgaObject of(FgaType type, UUID id) {
    return new FgaObject(type.fgaType(), id.toString());
  }

  /** Returns the wire format {@code <type>:<id>}. */
  public String formatted() {
    return type + ":" + id;
  }

  @Override
  public FgaObject toFgaObject() {
    return this;
  }

  @Override
  public String toString() {
    return formatted();
  }
}
