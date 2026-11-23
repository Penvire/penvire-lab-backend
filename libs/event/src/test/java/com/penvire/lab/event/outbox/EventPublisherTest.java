package com.penvire.lab.event.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penvire.lab.event.TestClockProducer;
import com.penvire.lab.event.TestEvents.WidgetCreated;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionalException;
import java.io.IOException;
import java.util.UUID;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EventPublisherTest {

  @Inject EventPublisher publisher;

  @Inject OutboxEventRepository outbox;

  @Test
  void storesTheEventWithItsCloudEventsAttributes() throws IOException {
    UUID id = publishInNewTransaction("widget.widget", "w-1", new WidgetCreated("w-1", "blue"));

    OutboxEventEntity row = find(id);
    assertEquals(7, id.version());
    assertEquals("widget.widget", row.topic);
    assertEquals("dev.penvire.lab.widget.widget-created.v1", row.type);
    assertEquals("/penvire-lab/widget", row.source);
    assertEquals("w-1", row.subject);
    assertEquals(TestClockProducer.NOW, row.time);
    assertEquals(
        "urn:penvire-lab:schema:dev.penvire.lab.widget.events.v1.WidgetCreated:2", row.dataschema);
    assertNull(row.tenantid);
    assertNull(row.publishedAt);

    GenericRecord payload = decode(row.payload);
    assertEquals("w-1", String.valueOf(payload.get("widgetId")));
    assertEquals("blue", String.valueOf(payload.get("colour")));
  }

  @Test
  void storesTheTenantOfATenantScopedEvent() {
    UUID id =
        QuarkusTransaction.requiringNew()
            .call(() ->
                publisher.publish(
                    "widget.widget", "w-2", "tenant-1", new WidgetCreated("w-2", null)));

    assertEquals("tenant-1", find(id).tenantid);
  }

  @Test
  void keepsTheTraceparentOfTheActiveSpan() {
    SpanContext span =
        SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault());
    UUID id;
    try (Scope ignored = Span.wrap(span).makeCurrent()) {
      id = publishInNewTransaction("widget.widget", "w-3", new WidgetCreated("w-3", null));
    }

    assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", find(id).traceparent);
  }

  @Test
  void storesNoTraceparentWithoutAnActiveSpan() {
    UUID id = publishInNewTransaction("widget.widget", "w-4", new WidgetCreated("w-4", null));

    assertNull(find(id).traceparent);
  }

  @Test
  void requiresTheCallersTransaction() {
    assertThrows(
        TransactionalException.class,
        () -> publisher.publish("widget.widget", "w-5", new WidgetCreated("w-5", null)));
  }

  @Test
  void aRolledBackChangeLeavesNoEvent() {
    assertThrows(
        IllegalStateException.class,
        () ->
            QuarkusTransaction.requiringNew()
                .run(() -> {
                  publisher.publish("widget.widget", "w-6", new WidgetCreated("w-6", null));
                  throw new IllegalStateException("the use case fails after publishing");
                }));

    assertEquals(0L, QuarkusTransaction.requiringNew().call(() -> outbox.count("subject", "w-6")));
  }

  @Test
  void rejectsATopicOfAnotherService() {
    assertThrows(
        IllegalArgumentException.class,
        () -> publishInNewTransaction("gadget.widget", "w-7", new WidgetCreated("w-7", null)));
  }

  private UUID publishInNewTransaction(String topic, String subject, WidgetCreated event) {
    return QuarkusTransaction.requiringNew().call(() -> publisher.publish(topic, subject, event));
  }

  private OutboxEventEntity find(UUID id) {
    return QuarkusTransaction.requiringNew().call(() -> outbox.findById(id));
  }

  private static GenericRecord decode(byte[] payload) throws IOException {
    return new GenericDatumReader<GenericRecord>(WidgetCreated.SCHEMA)
        .read(null, DecoderFactory.get().binaryDecoder(payload, null));
  }
}
