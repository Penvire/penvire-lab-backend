package com.penvire.lab.fga.model;

import java.util.Objects;

/**
 * A single OpenFGA relationship tuple ({@code subject}, {@code relation}, {@code object}) used for
 * writes, deletes, and reads.
 */
public record FgaTuple(FgaSubject subject, String relation, FgaObject object) {

  /**
   * Creates a validated relationship tuple.
   *
   * @throws IllegalArgumentException if {@code relation} is not a valid OpenFGA identifier
   */
  public FgaTuple {
    Objects.requireNonNull(subject, "subject");
    FgaValidation.requireRelation(relation);
    Objects.requireNonNull(object, "object");
  }

  /** Creates a relationship tuple from typed seams. */
  public static FgaTuple of(FgaPrincipal subject, FgaRelation relation, FgaResource object) {
    return new FgaTuple(subject.toFgaSubject(), relation.fgaRelation(), object.toFgaObject());
  }
}
