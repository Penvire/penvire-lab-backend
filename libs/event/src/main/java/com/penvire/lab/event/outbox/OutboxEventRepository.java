package com.penvire.lab.event.outbox;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
class OutboxEventRepository implements PanacheRepositoryBase<OutboxEventEntity, UUID> {
  /** Any constant works; it only has to be the same in every instance of a service. */
  private static final long RELAY_LOCK = 0x5045_4e56_4f42_4f58L; // "PENVOBOX"

  /**
   * Makes this transaction the only active relay. Released at commit or rollback.
   *
   * @return false if another instance is relaying right now
   */
  boolean tryBecomeRelay() {
    return (Boolean)
        getEntityManager()
            .createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
            .setParameter("key", RELAY_LOCK)
            .getSingleResult();
  }

  /** The oldest unpublished events, locked until the transaction ends. */
  @SuppressWarnings("unchecked") // Native queries are untyped; the entity class fixes the type.
  List<OutboxEventEntity> lockUnpublished(int limit) {
    return getEntityManager()
        .createNativeQuery(
            """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            OutboxEventEntity.class)
        .setParameter("limit", limit)
        .getResultList();
  }

  long deletePublishedBefore(Instant cutoff) {
    return delete("publishedAt < ?1", cutoff);
  }
}
