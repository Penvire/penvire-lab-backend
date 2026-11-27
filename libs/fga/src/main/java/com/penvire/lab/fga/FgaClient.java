package com.penvire.lab.fga;

import com.penvire.lab.fga.model.FgaObject;
import com.penvire.lab.fga.model.FgaPrincipal;
import com.penvire.lab.fga.model.FgaRelation;
import com.penvire.lab.fga.model.FgaResource;
import com.penvire.lab.fga.model.FgaTuple;
import com.penvire.lab.fga.model.FgaType;
import com.penvire.lab.fga.query.FgaTupleFilter;
import io.quarkus.security.ForbiddenException;
import java.util.List;

/**
 * Synchronous, blocking CDI facade over OpenFGA (ADR 0010).
 *
 * <p><strong>Threading:</strong> every method blocks the calling thread on network I/O up to {@code
 * penvire.fga.request-timeout}. Call only from worker or virtual threads, never from the Vert.x
 * event loop.
 *
 * <p>Reads and checks default to {@code HIGHER_CONSISTENCY} so revocations and new grants take
 * effect immediately. Writes ignore duplicate tuples and deletes ignore missing tuples. Outages,
 * timeouts, rate limits, and 5xx errors fail closed with {@link FgaUnavailableException}.
 */
public interface FgaClient {

  /**
   * Evaluates whether {@code subject} holds {@code relation} on {@code object}.
   *
   * @throws FgaUnavailableException if OpenFGA is unreachable, times out, or returns 429/5xx
   * @throws FgaException if OpenFGA rejects the request (for example an unknown relation)
   */
  boolean check(FgaPrincipal subject, FgaRelation relation, FgaResource object);

  /**
   * Atomically writes {@code writes} and deletes {@code deletes} in one OpenFGA transaction
   * (duplicate writes and missing deletes are ignored). Empty requests are a no-op.
   *
   * <p>OpenFGA caps a single transactional write request at 100 tuples total.
   *
   * @throws FgaUnavailableException if OpenFGA is unreachable, times out, or returns 429/5xx
   * @throws FgaException if OpenFGA rejects the write
   */
  void writeAndDelete(List<FgaTuple> writes, List<FgaTuple> deletes);

  /**
   * Reads all stored tuples matching {@code filter}, automatically following pagination tokens.
   *
   * @throws FgaUnavailableException if OpenFGA is unreachable, times out, or returns 429/5xx
   * @throws FgaException if OpenFGA rejects the read
   */
  List<FgaTuple> read(FgaTupleFilter filter);

  /**
   * Lists objects of {@code objectType} for which {@code subject} holds {@code relation}.
   *
   * <p><strong>Bounded query:</strong> OpenFGA caps {@code listObjects} responses by server result
   * limit and evaluation deadline, and does not signal truncation. Callers that require exhaustive
   * enumeration over large datasets must query their own paginated domain store (for example
   * filtering by tenant) or use a streaming enumeration path instead.
   *
   * @throws FgaUnavailableException if OpenFGA is unreachable, times out, or returns 429/5xx
   * @throws FgaException if OpenFGA rejects the query
   */
  List<FgaObject> listObjects(FgaPrincipal subject, FgaRelation relation, FgaType objectType);

  /**
   * Enforces that {@code subject} holds {@code relation} on {@code object}, throwing Quarkus {@link
   * ForbiddenException} when denied so {@code @Audited} actions record {@code DENIED} automatically
   * (ADR 0008, ADR 0010).
   *
   * @throws ForbiddenException if the subject lacks the required relation
   * @throws FgaUnavailableException if OpenFGA is unavailable (fails closed)
   */
  default void require(FgaPrincipal subject, FgaRelation relation, FgaResource object) {
    if (!check(subject, relation, object)) {
      throw new ForbiddenException(
          "Subject %s lacks %s on %s"
              .formatted(
                  subject.toFgaSubject().formatted(),
                  relation.fgaRelation(),
                  object.toFgaObject().formatted()));
    }
  }

  /** Idempotently writes {@code tuples} (duplicates are ignored). */
  default void write(List<FgaTuple> tuples) {
    writeAndDelete(tuples, List.of());
  }

  /** Idempotently writes {@code tuples} (duplicates are ignored). */
  default void write(FgaTuple... tuples) {
    write(List.of(tuples));
  }

  /** Idempotently deletes {@code tuples} (missing tuples are ignored). */
  default void delete(List<FgaTuple> tuples) {
    writeAndDelete(List.of(), tuples);
  }

  /** Idempotently deletes {@code tuples} (missing tuples are ignored). */
  default void delete(FgaTuple... tuples) {
    delete(List.of(tuples));
  }
}
