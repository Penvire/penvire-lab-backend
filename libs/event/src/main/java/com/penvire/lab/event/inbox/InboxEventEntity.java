package com.penvire.lab.event.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** An event one consumer has handled. Only cleanup reads it; claiming uses a native insert. */
@Entity
@Table(name = "inbox_event")
@SuppressWarnings("NullAway.Init")
class InboxEventEntity {
  @EmbeddedId Key key;

  @Column(name = "processed_at")
  Instant processedAt;

  @Embeddable
  @SuppressWarnings("NullAway.Init")
  record Key(String consumer, @Column(name = "event_id") UUID eventId) implements Serializable {}
}
