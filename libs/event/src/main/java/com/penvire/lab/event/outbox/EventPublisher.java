package com.penvire.lab.event.outbox;

import com.penvire.lab.event.EventType;
import com.penvire.lab.event.SchemaCatalog;
import com.penvire.lab.persistence.IdGenerator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.jspecify.annotations.Nullable;

/**
 * Publishes events through the transactional outbox (ADR 0004). The event is stored in the caller's
 * transaction, so it exists if and only if the change it describes commits; the relay sends it.
 */
@ApplicationScoped
public class EventPublisher {

  private final OutboxEventRepository outbox;
  private final SchemaCatalog schemas;
  private final IdGenerator ids;
  private final Clock clock;

  EventPublisher(
      OutboxEventRepository outbox, SchemaCatalog schemas, IdGenerator ids, Clock clock) {
    this.outbox = outbox;
    this.schemas = schemas;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Publishes a platform-scoped event.
   *
   * @param topic {@code <service>.<aggregate>}, owned by the event's service
   * @param subject the aggregate ID; also the record key, so one aggregate's events stay in order
   * @return the event ID
   */
  @Transactional(Transactional.TxType.MANDATORY)
  public UUID publish(String topic, String subject, SpecificRecordBase payload) {
    return append(topic, subject, null, payload);
  }

  /**
   * Publishes an event owned by one tenant; see {@link #publish(String, String,
   * SpecificRecordBase)}.
   */
  @Transactional(Transactional.TxType.MANDATORY)
  public UUID publish(String topic, String subject, String tenantId, SpecificRecordBase payload) {
    return append(topic, subject, tenantId, payload);
  }

  /**
   * Publishes an event with nothing to stay in order with; its own ID becomes the subject and
   * record key (ADR 0006: an anonymous caller refused before a resource existed).
   *
   * @param tenantId the owning tenant, or null for a platform-scoped event
   */
  @Transactional(Transactional.TxType.MANDATORY)
  public UUID publishKeyedById(
      String topic, @Nullable String tenantId, SpecificRecordBase payload) {
    return append(topic, null, tenantId, payload);
  }

  private UUID append(
      String topic,
      @Nullable String subject,
      @Nullable String tenantId,
      SpecificRecordBase payload) {
    EventType eventType = EventType.of(payload.getSchema());
    if (!topic.startsWith(eventType.service() + ".")) {
      throw new IllegalArgumentException(
          eventType.type() + " belongs to " + eventType.service() + ", not to topic " + topic);
    }

    OutboxEventEntity row = new OutboxEventEntity();
    row.id = ids.next();
    row.source = eventType.source();
    row.subject = subject != null ? subject : row.id.toString();
    row.topic = topic;
    row.type = eventType.type();
    row.time = clock.instant();
    row.dataschema = schemas.latestOf(payload).dataSchema().urn();
    row.traceparent = currentTraceparent();
    row.tenantid = tenantId;
    row.payload = encode(payload);
    outbox.persist(row);
    return row.id;
  }

  /** The Avro binary encoding alone: the schema travels as {@code dataschema}, not in the value. */
  private static byte[] encode(SpecificRecordBase payload) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(out, null);
    try {
      new SpecificDatumWriter<SpecificRecordBase>(payload.getSchema(), payload.getSpecificData())
          .write(payload, encoder);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not encode " + payload.getSchema().getFullName(), e);
    }
    return out.toByteArray();
  }

  /** The W3C traceparent of the active span, so the relay can continue the request's trace. */
  private static @Nullable String currentTraceparent() {
    Map<String, String> carrier = new HashMap<>();
    W3CTraceContextPropagator.getInstance()
        .inject(
            Context.current(),
            carrier,
            (map, key, value) -> {
              if (map != null) {
                map.put(key, value);
              }
            });
    return carrier.get("traceparent");
  }
}
