package com.penvire.lab.fga;

import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaPrincipal;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaResource;
import com.penvire.lab.fga.model.FgaSubject;
import com.penvire.lab.fga.model.FgaTuple;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.model.FgaValidation;
import com.penvire.lab.fga.query.FgaTupleFilter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory {@link FgaClient} test fixture for unit tests outside OpenFGA.
 *
 * <p><strong>Limitations:</strong> this fake stores a flat set of tuples. It does not evaluate an
 * OpenFGA authorization model, does not expand usersets, and accepts grants on computed permissions
 * that real OpenFGA refuses to store. Use it to verify that a service checks or writes the expected
 * relation, and test the authorization model itself against real OpenFGA ({@code fga model test} or
 * Testcontainers).
 */
public final class InMemoryFgaClient implements FgaClient {

  private final Object writeLock = new Object();
  private final AtomicReference<Set<FgaTuple>> stored = new AtomicReference<>(Set.of());
  private final AtomicBoolean unavailable = new AtomicBoolean(false);

  /** Directly seeds {@code (subject, relation, object)} without triggering availability checks. */
  public void grant(FgaPrincipal subject, FgaRelation relation, FgaResource object) {
    FgaTuple tuple = FgaTuple.of(subject, relation, object);
    synchronized (writeLock) {
      Set<FgaTuple> next = new HashSet<>(stored.get());
      next.add(tuple);
      stored.set(Set.copyOf(next));
    }
  }

  /**
   * Directly removes {@code (subject, relation, object)} without triggering availability checks.
   */
  public void revoke(FgaPrincipal subject, FgaRelation relation, FgaResource object) {
    FgaTuple tuple = FgaTuple.of(subject, relation, object);
    synchronized (writeLock) {
      Set<FgaTuple> next = new HashSet<>(stored.get());
      next.remove(tuple);
      stored.set(Set.copyOf(next));
    }
  }

  /**
   * When {@code true}, every {@link FgaClient} call fails closed with {@link
   * FgaUnavailableException}.
   */
  public void setUnavailable(boolean unavailable) {
    this.unavailable.set(unavailable);
  }

  /** Removes all stored tuples. */
  public void clear() {
    synchronized (writeLock) {
      stored.set(Set.of());
    }
  }

  /** Returns an immutable snapshot of all stored tuples. */
  public Set<FgaTuple> tuples() {
    return stored.get();
  }

  @Override
  public boolean check(FgaPrincipal subject, FgaRelation relation, FgaResource object) {
    requireAvailable();
    return stored.get().contains(FgaTuple.of(subject, relation, object));
  }

  @Override
  public void writeAndDelete(List<FgaTuple> writes, List<FgaTuple> deletes) {
    requireAvailable();
    List<FgaTuple> validatedWrites = List.copyOf(Objects.requireNonNull(writes, "writes"));
    List<FgaTuple> validatedDeletes = List.copyOf(Objects.requireNonNull(deletes, "deletes"));
    if (validatedWrites.isEmpty() && validatedDeletes.isEmpty()) {
      return;
    }

    synchronized (writeLock) {
      Set<FgaTuple> next = new HashSet<>(stored.get());
      validatedDeletes.forEach(next::remove);
      next.addAll(validatedWrites);
      stored.set(Set.copyOf(next));
    }
  }

  @Override
  public List<FgaTuple> read(FgaTupleFilter filter) {
    requireAvailable();
    Objects.requireNonNull(filter, "filter");
    return stored.get().stream().filter(t -> matchesFilter(t, filter)).toList();
  }

  @Override
  public List<FgaObject> listObjects(
      FgaPrincipal subject, FgaRelation relation, FgaType objectType) {
    requireAvailable();
    FgaSubject target = subject.toFgaSubject();
    String rel = FgaValidation.requireRelation(relation.fgaRelation());
    String type = FgaValidation.requireType(objectType.fgaType());

    return stored.get().stream()
        .filter(t ->
            t.subject().equals(target)
                && t.relation().equals(rel)
                && t.object().type().equals(type))
        .map(FgaTuple::object)
        .distinct()
        .toList();
  }

  private static boolean matchesFilter(FgaTuple tuple, FgaTupleFilter filter) {
    if (filter.subject() != null && !tuple.subject().equals(filter.subject())) {
      return false;
    }
    if (filter.relation() != null && !tuple.relation().equals(filter.relation())) {
      return false;
    }
    if (filter.object() != null && !tuple.object().equals(filter.object())) {
      return false;
    }
    return filter.objectType() == null || tuple.object().type().equals(filter.objectType());
  }

  private void requireAvailable() {
    if (unavailable.get()) {
      throw new FgaUnavailableException("Simulated OpenFGA outage");
    }
  }
}
