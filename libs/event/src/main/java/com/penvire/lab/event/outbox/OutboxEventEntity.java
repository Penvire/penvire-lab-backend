package com.penvire.lab.event.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An event waiting for the relay, with its CloudEvents attributes and Avro-encoded payload. */
@Entity
@Table(name = "outbox_event")
@SuppressWarnings("NullAway.Init") // Hibernate fills the fields after the no-arg constructor
class OutboxEventEntity {
  @Id UUID id;

  String topic;

  String type;

  String source;

  String subject;

  Instant time;

  String dataschema;

  @Nullable String traceparent;

  @Nullable String tenantid;

  byte[] payload;

  @Column(name = "published_at")
  @Nullable Instant publishedAt;
}
