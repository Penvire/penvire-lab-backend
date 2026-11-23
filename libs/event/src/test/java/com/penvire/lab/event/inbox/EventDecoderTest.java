package com.penvire.lab.event.inbox;

import static com.penvire.lab.event.TestCloudEvents.WIDGET_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.event.DataSchema;
import com.penvire.lab.event.SchemaCatalog;
import com.penvire.lab.event.TestCloudEvents;
import dev.penvire.lab.widget.events.v1.WidgetCreated;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventDecoderTest {

  private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private final EventDecoder decoder = new EventDecoder(new SchemaCatalog());

  @Test
  void readsTheAttributesAndThePayload() {
    ConsumerRecord<String, byte[]> record = latest(new WidgetCreated("w-1", "blue"));
    TestCloudEvents.add(record, "ce_tenantid", "tenant-1");
    TestCloudEvents.add(record, "ce_traceparent", TRACEPARENT);

    IncomingEvent event = decoder.decode(record).orElseThrow();

    assertEquals(ID, event.id());
    assertEquals("/penvire-lab/widget", event.source());
    assertEquals("dev.penvire.lab.widget.widget-created.v1", event.type());
    assertEquals("w-1", event.subject());
    assertEquals(Instant.parse("2030-01-01T12:00:00Z"), event.time());
    assertEquals(new DataSchema(WIDGET_CREATED, 2), event.dataSchema());
    assertEquals("tenant-1", event.tenantId());
    assertEquals(TRACEPARENT, event.traceparent());
    assertEquals(new WidgetCreated("w-1", "blue"), event.payload());
  }

  @Test
  void leavesOptionalAttributesEmpty() {
    IncomingEvent event = decoder.decode(latest(new WidgetCreated("w-1", null))).orElseThrow();

    assertNull(event.tenantId());
    assertNull(event.traceparent());
  }

  @Test
  void resolvesAnOlderRevisionIntoTheConsumersClass() {
    Schema revision1 = TestCloudEvents.revision(WIDGET_CREATED, 1);
    byte[] payload =
        TestCloudEvents.encode(
            new GenericRecordBuilder(revision1).set("widgetId", "w-1").build(), revision1);

    IncomingEvent event =
        decoder
            .decode(TestCloudEvents.record(ID, new DataSchema(WIDGET_CREATED, 1), payload))
            .orElseThrow();

    assertEquals(new WidgetCreated("w-1", null), event.payload(), "colour takes its default");
  }

  @Test
  void skipsATypeTheConsumerHasNoClassFor() {
    DataSchema deleted = new DataSchema("dev.penvire.lab.widget.events.v1.WidgetDeleted", 1);

    assertTrue(decoder.decode(TestCloudEvents.record(ID, deleted, new byte[0])).isEmpty());
  }

  @Test
  void rejectsARevisionTheConsumersPackageDoesNotShip() {
    DataSchema newer = new DataSchema(WIDGET_CREATED, 3);
    ConsumerRecord<String, byte[]> record = TestCloudEvents.record(ID, newer, new byte[0]);

    assertThrows(IllegalStateException.class, () -> decoder.decode(record));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ce_id", "ce_source", "ce_type", "ce_subject", "ce_time", "ce_dataschema"})
  void rejectsARecordWithoutARequiredHeader(String header) {
    ConsumerRecord<String, byte[]> record = latest(new WidgetCreated("w-1", null));
    record.headers().remove(header);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(record));
  }

  private static ConsumerRecord<String, byte[]> latest(WidgetCreated payload) {
    return TestCloudEvents.record(
        ID,
        new DataSchema(WIDGET_CREATED, 2),
        TestCloudEvents.encode(payload, WidgetCreated.getClassSchema()));
  }
}
