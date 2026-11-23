package com.penvire.lab.event.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OutboxRecordsTest {

  private static final UUID ID = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  @Test
  void sendsTheRowAsACloudEventInBinaryMode() {
    OutboxEventEntity row = row();
    row.traceparent = TRACEPARENT;
    row.tenantid = "tenant-1";

    ProducerRecord<String, byte[]> record = OutboxRecords.toRecord(row);

    assertEquals("widget.widget", record.topic());
    assertEquals("w-1", record.key());
    assertArrayEquals(new byte[] {1, 2, 3}, record.value());
    assertEquals("1.0", header(record, "ce_specversion"));
    assertEquals(ID.toString(), header(record, "ce_id"));
    assertEquals("/penvire-lab/widget", header(record, "ce_source"));
    assertEquals("dev.penvire.lab.widget.widget-created.v1", header(record, "ce_type"));
    assertEquals("w-1", header(record, "ce_subject"));
    assertEquals("2030-01-01T12:00:00Z", header(record, "ce_time"));
    assertEquals(
        "urn:penvire-lab:schema:dev.penvire.lab.widget.events.v1.WidgetCreated:2",
        header(record, "ce_dataschema"));
    assertEquals("application/avro", header(record, "content-type"));
    assertEquals(TRACEPARENT, header(record, "ce_traceparent"));
    assertEquals("tenant-1", header(record, "ce_tenantid"));
  }

  @Test
  void leavesOutTraceAndTenantWhenTheRowHasNone() {
    ProducerRecord<String, byte[]> record = OutboxRecords.toRecord(row());

    assertNull(header(record, "ce_traceparent"));
    assertNull(header(record, "ce_tenantid"));
  }

  private static OutboxEventEntity row() {
    OutboxEventEntity row = new OutboxEventEntity();
    row.id = ID;
    row.topic = "widget.widget";
    row.type = "dev.penvire.lab.widget.widget-created.v1";
    row.source = "/penvire-lab/widget";
    row.subject = "w-1";
    row.time = Instant.parse("2030-01-01T12:00:00Z");
    row.dataschema = "urn:penvire-lab:schema:dev.penvire.lab.widget.events.v1.WidgetCreated:2";
    row.payload = new byte[] {1, 2, 3};
    return row;
  }

  private static @Nullable String header(ProducerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), UTF_8);
  }
}
