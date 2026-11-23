package com.penvire.lab.event.inbox;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
class InboxEventRepository
    implements PanacheRepositoryBase<InboxEventEntity, InboxEventEntity.Key> {

  /**
   * Records the event for the consumer, unless it already is. The row is locked until the
   * transaction ends, so a concurrent delivery of the same event waits here, then gets false.
   *
   * @return true if this transaction should handle the event
   */
  boolean claim(String consumer, UUID eventId, Instant now) {
    return getEntityManager()
            .createNativeQuery(
                """
                INSERT INTO inbox_event (consumer, event_id, processed_at)
                VALUES (:consumer, :eventId, :now)
                ON CONFLICT DO NOTHING
                """)
            .setParameter("consumer", consumer)
            .setParameter("eventId", eventId)
            .setParameter("now", now)
            .executeUpdate()
        == 1;
  }

  long deleteProcessedBefore(Instant cutoff) {
    return delete("processedAt < ?1", cutoff);
  }
}
