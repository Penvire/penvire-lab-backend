package com.penvire.lab.event;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Incoming records shaped like the relay's, for consumer tests that bypass Kafka. */
public final class TestCloudEvents {

  public static final String WIDGET_CREATED = "dev.penvire.lab.widget.events.v1.WidgetCreated";

  private TestCloudEvents() {}

  /** A {@code widget.widget} record with every required CloudEvents header. */
  public static ConsumerRecord<String, byte[]> record(
      UUID id, DataSchema dataSchema, byte[] payload) {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("widget.widget", 0, 0L, "w-1", payload);
    add(record, "ce_specversion", "1.0");
    add(record, "ce_id", id.toString());
    add(record, "ce_source", "/penvire-lab/widget");
    add(record, "ce_type", "dev.penvire.lab.widget.widget-created.v1");
    add(record, "ce_subject", "w-1");
    add(record, "ce_time", "2030-01-01T12:00:00Z");
    add(record, "ce_dataschema", dataSchema.urn());
    add(record, "content-type", "application/avro");
    return record;
  }

  public static void add(ConsumerRecord<String, byte[]> record, String name, String value) {
    record.headers().add(name, value.getBytes(UTF_8));
  }

  /** The plain Avro binary encoding of the record, written with the given schema. */
  public static byte[] encode(IndexedRecord payload, Schema writer) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(out, null);
    try {
      new GenericDatumWriter<IndexedRecord>(writer).write(payload, encoder);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /** A revision shipped under src/test/resources/avro. */
  public static Schema revision(String fullName, int revision) {
    String resource = "/avro/" + fullName + "/" + revision + ".avsc";
    try (InputStream in = TestCloudEvents.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalArgumentException("No test schema " + resource);
      }
      return new Schema.Parser().parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
