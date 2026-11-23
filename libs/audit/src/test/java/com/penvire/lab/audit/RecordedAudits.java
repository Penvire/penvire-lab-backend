package com.penvire.lab.audit;

import dev.penvire.lab.widget.events.v1.AuditRecorded;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;

/** Reads the audit events the library left in the outbox, and the widgets that committed. */
@ApplicationScoped
public class RecordedAudits {

  /** One outbox row on {@code widget.audit}, with its payload decoded. */
  public record Row(UUID id, String topic, String type, String subject, AuditRecorded event) {}

  private final EntityManager entityManager;

  RecordedAudits(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /** The audit events with this subject, oldest first. */
  public List<Row> withSubject(String subject) {
    return rows(() ->
        entityManager
            .createNativeQuery(SELECT_ROWS + " AND subject = :subject ORDER BY id")
            .setParameter("subject", subject));
  }

  /** The audit events of this action, oldest first. */
  public List<Row> ofAction(String action) {
    return rows(() -> entityManager.createNativeQuery(SELECT_ROWS + " ORDER BY id")).stream()
        .filter(row -> row.event().getAction().toString().equals(action))
        .toList();
  }

  public boolean widgetExists(UUID id) {
    Number count =
        QuarkusTransaction.requiringNew()
            .call(() ->
                (Number)
                    entityManager
                        .createNativeQuery("SELECT count(*) FROM widget WHERE id = :id")
                        .setParameter("id", id)
                        .getSingleResult());
    return count.longValue() > 0;
  }

  private static final String SELECT_ROWS =
      "SELECT id, topic, type, subject, payload FROM outbox_event WHERE topic = 'widget.audit'";

  private static List<Row> rows(Supplier<Query> query) {
    List<?> results = QuarkusTransaction.requiringNew().call(() -> query.get().getResultList());
    return results.stream().map(RecordedAudits::row).toList();
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
