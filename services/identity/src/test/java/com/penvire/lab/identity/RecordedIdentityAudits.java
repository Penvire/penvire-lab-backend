package com.penvire.lab.identity;

import dev.penvire.lab.identity.events.v1.AuditRecorded;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;

/** Reads {@code identity.audit} rows from the outbox table and decodes their Avro payload. */
@ApplicationScoped
class RecordedIdentityAudits {

  record Row(UUID id, String topic, String type, String subject, AuditRecorded event) {}

  private final EntityManager entityManager;

  RecordedIdentityAudits(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  void clear() {
    QuarkusTransaction.requiringNew()
        .run(() ->
            entityManager
                .createNativeQuery("DELETE FROM outbox_event WHERE topic = 'identity.audit'")
                .executeUpdate());
  }

  List<Row> ofAction(String action) {
    return all().stream().filter(row -> row.event().getAction().toString().equals(action)).toList();
  }

  private List<Row> all() {
    List<?> results =
        QuarkusTransaction.requiringNew()
            .call(() ->
                entityManager
                    .createNativeQuery(
                        "SELECT id, topic, type, subject, payload FROM outbox_event"
                            + " WHERE topic = 'identity.audit' ORDER BY id")
                    .getResultList());
    return results.stream().map(RecordedIdentityAudits::row).toList();
  }

  private static Row row(Object result) {
    Object[] columns = (Object[]) result;
    return new Row(
        (UUID) columns[0],
        (String) columns[1],
        (String) columns[2],
        (String) columns[3],
        decode((byte[]) columns[4]));
  }

  private static AuditRecorded decode(byte[] payload) {
    try {
      return new SpecificDatumReader<>(AuditRecorded.class)
          .read(null, DecoderFactory.get().binaryDecoder(payload, null));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
