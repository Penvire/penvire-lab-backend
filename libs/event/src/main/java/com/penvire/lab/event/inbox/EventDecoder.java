package com.penvire.lab.event.inbox;

import com.penvire.lab.event.DataSchema;
import com.penvire.lab.event.EventType;
import com.penvire.lab.event.SchemaCatalog;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;

/** Reads a CloudEvent in Kafka binary content mode back into an {@link IncomingEvent}. */
@Singleton
class EventDecoder {

  static {
    EventType.ensureTrusted();
  }

  private final SchemaCatalog schemas;
  private final Map<String, Optional<Class<? extends SpecificRecordBase>>> readerClasses =
      new ConcurrentHashMap<>();

  EventDecoder(SchemaCatalog schemas) {
    this.schemas = schemas;
  }

  /**
   * Decodes the record with the schema it was written with, resolved against the consumer's class.
   *
   * @return empty if the consumer has no class for the event: it does not consume that type
   * @throws IllegalArgumentException if a required CloudEvents header is missing or malformed
   * @throws IllegalStateException if the consumer's package lacks the event's schema revision
   */
  Optional<IncomingEvent> decode(ConsumerRecord<String, byte[]> record) {
    DataSchema dataSchema = DataSchema.parse(required(record, "ce_dataschema"));
    Optional<Class<? extends SpecificRecordBase>> readerClass =
        readerClasses.computeIfAbsent(dataSchema.fullName(), EventDecoder::findReaderClass);
    if (readerClass.isEmpty()) {
      return Optional.empty();
    }

    Schema writer = schemas.writerOf(dataSchema, readerClass.get()).schema();
    return Optional.of(
        new IncomingEvent(
            UUID.fromString(required(record, "ce_id")),
            required(record, "ce_source"),
            required(record, "ce_type"),
            required(record, "ce_subject"),
            Instant.parse(required(record, "ce_time")),
            dataSchema,
            header(record, "ce_tenantid"),
            header(record, "ce_traceparent"),
            read(record.value(), writer, readerClass.get())));
  }

  /** Generated classes are named after the schema's full name. */
  private static Optional<Class<? extends SpecificRecordBase>> findReaderClass(String fullName) {
    try {
      Class<?> type = Class.forName(fullName, false, applicationClassLoader());
      if (!SpecificRecordBase.class.isAssignableFrom(type)) {
        throw new IllegalStateException(fullName + " is not a generated Avro class");
      }
      return Optional.of(type.asSubclass(SpecificRecordBase.class));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  /**
   * The thread's context class loader, which in Quarkus sees the application's classes. Without
   * one, Class.forName would search only the bootstrap loader and every event would be skipped.
   */
  private static ClassLoader applicationClassLoader() {
    ClassLoader context = Thread.currentThread().getContextClassLoader();
    return context != null ? context : EventDecoder.class.getClassLoader();
  }

  private static SpecificRecordBase read(
      byte[] value, Schema writer, Class<? extends SpecificRecordBase> readerClass) {
    SpecificData data = SpecificData.getForClass(readerClass);
    Schema reader = data.getSchema(readerClass);
    try {
      return new SpecificDatumReader<SpecificRecordBase>(writer, reader, data)
          .read(null, DecoderFactory.get().binaryDecoder(value, null));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not decode " + writer.getFullName(), e);
    }
  }

  private static String required(ConsumerRecord<String, byte[]> record, String name) {
    String value = header(record, name);
    if (value == null) {
      throw new IllegalArgumentException("Missing header: " + name);
    }
    return value;
  }

  private static @Nullable String header(ConsumerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }
}
