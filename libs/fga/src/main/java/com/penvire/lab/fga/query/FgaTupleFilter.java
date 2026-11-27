package com.penvire.lab.fga.query;

import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaPrincipal;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaResource;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.model.FgaValidation;
import org.jspecify.annotations.Nullable;

/**
 * Filter for reading stored tuples from OpenFGA (for example cleaning up all tuples on resource
 * delete or diffing a custom role's permissions).
 *
 * <p>Enforces OpenFGA's read-filter constraints at construction time:
 *
 * <ul>
 *   <li>{@code object} and {@code objectType} are mutually exclusive.
 *   <li>An {@code objectType} prefix ({@code type:}) requires a {@code subject}.
 *   <li>Specifying {@code subject} or {@code relation} requires either {@code object} or {@code
 *       objectType}.
 * </ul>
 */
public record FgaTupleFilter(
    @Nullable FgaSubject subject,
    @Nullable String relation,
    @Nullable FgaObject object,
    @Nullable String objectType) {

  /** Matches every stored tuple in the store. */
  public static final FgaTupleFilter ALL = new FgaTupleFilter(null, null, null, null);

  /**
   * Creates a validated tuple filter matching OpenFGA's read constraints.
   *
   * @throws IllegalArgumentException if {@code relation} or {@code objectType} is invalid, or if
   *     the field combination is rejected by OpenFGA
   */
  public FgaTupleFilter {
    if (relation != null) {
      FgaValidation.requireRelation(relation);
    }

    if (objectType != null) {
      FgaValidation.requireType(objectType);
    }

    if (object != null && objectType != null) {
      throw new IllegalArgumentException("Cannot set both object and objectType");
    }

    if (objectType != null && subject == null) {
      throw new IllegalArgumentException("objectType prefix filter requires a subject");
    }

    if (object == null && objectType == null && (subject != null || relation != null)) {
      throw new IllegalArgumentException(
          "subject or relation filter requires an object or objectType");
    }
  }

  /** Matches all tuples pointing to {@code object}. */
  public static FgaTupleFilter forObject(FgaResource object) {
    return new FgaTupleFilter(null, null, object.toFgaObject(), null);
  }

  /** Matches tuples pointing to {@code object} with the given {@code relation}. */
  public static FgaTupleFilter forObject(FgaResource object, FgaRelation relation) {
    return new FgaTupleFilter(null, relation.fgaRelation(), object.toFgaObject(), null);
  }

  /** Matches all tuples for {@code subject} on any object of {@code objectType}. */
  public static FgaTupleFilter forSubject(FgaPrincipal subject, FgaType objectType) {
    return new FgaTupleFilter(subject.toFgaSubject(), null, null, objectType.fgaType());
  }

  /**
   * Matches tuples for {@code subject} and {@code relation} on any object of {@code objectType}.
   */
  public static FgaTupleFilter forSubject(
      FgaPrincipal subject, FgaRelation relation, FgaType objectType) {
    return new FgaTupleFilter(
        subject.toFgaSubject(), relation.fgaRelation(), null, objectType.fgaType());
  }

  /**
   * Formats the OpenFGA {@code object} read filter: {@code type:id} for an exact object, {@code
   * type:} for an object-type prefix, or {@code null} when unfiltered.
   */
  public @Nullable String formattedObjectFilter() {
    if (object != null) {
      return object.formatted();
    }
    return objectType == null ? null : objectType + ":";
  }
}
