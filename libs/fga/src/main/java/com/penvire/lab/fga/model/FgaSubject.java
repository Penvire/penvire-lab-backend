package com.penvire.lab.fga.model;

import java.util.UUID;

/**
 * Validated OpenFGA subject ({@code user} field in tuples). Modeled as a sealed hierarchy of {@link
 * Direct} ({@code user:<sub>}, {@code client:<client_id>}) and {@link Userset} ({@code
 * <type>:<id>#<relation>}, such as {@code role:<id>#assignee}).
 *
 * <p>All IDs are validated by {@link FgaValidation#requireId}, rejecting {@code *}, {@code :},
 * {@code #}, and whitespace at construction time so untrusted input cannot become a wildcard or
 * userset grant.
 */
public sealed interface FgaSubject extends FgaPrincipal
    permits FgaSubject.Direct, FgaSubject.Userset {

  /** Standard OpenFGA type for human users, keyed by JWT {@code sub} (ADR 0010). */
  FgaType USER_TYPE = FgaType.of("user");

  /** Standard OpenFGA type for autonomous service clients, keyed by {@code client_id}. */
  FgaType CLIENT_TYPE = FgaType.of("client");

  /** Returns the subject's OpenFGA type name. */
  String type();

  /** Returns the subject's OpenFGA identifier. */
  String id();

  /** Returns the wire format ({@code <type>:<id>} or {@code <type>:<id>#<relation>}). */
  String formatted();

  @Override
  default FgaSubject toFgaSubject() {
    return this;
  }

  /** Direct subject formatted as {@code <type>:<id>}. */
  record Direct(String type, String id) implements FgaSubject {

    /**
     * Creates a validated direct subject.
     *
     * @throws IllegalArgumentException if {@code type} or {@code id} violates OpenFGA syntax
     */
    public Direct {
      FgaValidation.requireType(type);
      FgaValidation.requireId(id);
    }

    @Override
    public String formatted() {
      return type + ":" + id;
    }

    @Override
    public String toString() {
      return formatted();
    }
  }

  /** Userset subject formatted as {@code <type>:<id>#<relation>}. */
  record Userset(String type, String id, String relation) implements FgaSubject {

    /**
     * Creates a validated userset subject.
     *
     * @throws IllegalArgumentException if {@code type}, {@code id}, or {@code relation} violates
     *     OpenFGA syntax
     */
    public Userset {
      FgaValidation.requireType(type);
      FgaValidation.requireId(id);
      FgaValidation.requireRelation(relation);
    }

    @Override
    public String formatted() {
      return type + ":" + id + "#" + relation;
    }

    @Override
    public String toString() {
      return formatted();
    }
  }

  /** Creates a human user subject ({@code user:<id>}). */
  static Direct user(String id) {
    return of(USER_TYPE, id);
  }

  /** Creates a human user subject ({@code user:<uuid>}). */
  static Direct user(UUID id) {
    return of(USER_TYPE, id);
  }

  /** Creates an autonomous client subject ({@code client:<id>}). */
  static Direct client(String id) {
    return of(CLIENT_TYPE, id);
  }

  /** Creates an autonomous client subject ({@code client:<uuid>}). */
  static Direct client(UUID id) {
    return of(CLIENT_TYPE, id);
  }

  /** Creates a direct subject from a typed {@link FgaType} and ID string. */
  static Direct of(FgaType type, String id) {
    return new Direct(type.fgaType(), id);
  }

  /** Creates a direct subject from a typed {@link FgaType} and UUID. */
  static Direct of(FgaType type, UUID id) {
    return new Direct(type.fgaType(), id.toString());
  }

  /**
   * Creates a direct subject from a resource, used for structural parent tuples such as {@code
   * tenant:<id>} as the subject of an engagement's {@code tenant} relation.
   */
  static Direct of(FgaResource resource) {
    FgaObject object = resource.toFgaObject();
    return new Direct(object.type(), object.id());
  }

  /**
   * Creates a userset subject ({@code <type>:<id>#<relation>}), such as {@code role:<id>#assignee}
   * for dynamic role grants.
   */
  static Userset userset(FgaResource resource, FgaRelation relation) {
    FgaObject object = resource.toFgaObject();
    return new Userset(object.type(), object.id(), relation.fgaRelation());
  }
}
