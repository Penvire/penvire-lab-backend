package com.penvire.lab.event.inbox;

import static com.penvire.lab.event.TestCloudEvents.WIDGET_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.event.DataSchema;
import com.penvire.lab.event.TestClockProducer;
import com.penvire.lab.event.TestCloudEvents;
import dev.penvire.lab.widget.events.v1.WidgetCreated;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Calls the consumer directly with records shaped like the relay's; no Kafka involved. */
@QuarkusTest
class EventConsumerTest {

  @Inject EventConsumer consumer;

  @Inject InboxEventRepository inbox;

  @Inject TransactionManager transactions;

  @Test
  void handlesAnEventOncePerConsumer() {
    UUID id = UUID.randomUUID();
    ConsumerRecord<String, byte[]> record = widgetCreated(id);
    AtomicInteger calls = new AtomicInteger();

    consumer.handle("audit", record, event -> calls.incrementAndGet());
    consumer.handle("audit", record, event -> calls.incrementAndGet());

    assertEquals(1, calls.get());
    assertTrue(inboxed("audit", id));
  }

  @Test
  void eachConsumerHandlesTheEventItself() {
    UUID id = UUID.randomUUID();
    AtomicInteger calls = new AtomicInteger();

    consumer.handle("audit", widgetCreated(id), event -> calls.incrementAndGet());
    consumer.handle("billing", widgetCreated(id), event -> calls.incrementAndGet());

    assertEquals(2, calls.get());
  }

  @Test
  void handlesTheEventInsideATransaction() {
    AtomicBoolean active = new AtomicBoolean();

    consumer.handle(
        "audit", widgetCreated(UUID.randomUUID()), event -> active.set(transactionActive()));

    assertTrue(active.get());
  }

  @Test
  void retriesAFailingHandlerUntilItSucceeds() {
    UUID id = UUID.randomUUID();
    AtomicInteger calls = new AtomicInteger();

    consumer.handle(
        "audit",
        widgetCreated(id),
        event -> {
          if (calls.incrementAndGet() < 3) {
            throw new IllegalStateException("not yet");
          }
        });

    assertEquals(3, calls.get());
    assertTrue(inboxed("audit", id));
  }

  @Test
  void throwsTheLastFailureAndRecordsNothing() {
    UUID id = UUID.randomUUID();
    AtomicInteger calls = new AtomicInteger();
    IllegalStateException failure = new IllegalStateException("always");

    var thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                consumer.handle(
                    "audit",
                    widgetCreated(id),
                    event -> {
                      calls.incrementAndGet();
                      throw failure;
                    }));

    assertSame(failure, thrown);
    assertEquals(3, calls.get(), "penvire.event.consumer.attempts defaults to 3");
    assertFalse(inboxed("audit", id), "every attempt rolled back its inbox entry");
  }

  @Test
  void skipsATypeTheConsumerHasNoClassFor() {
    DataSchema deleted = new DataSchema("dev.penvire.lab.widget.events.v1.WidgetDeleted", 1);
    AtomicInteger calls = new AtomicInteger();

    consumer.handle(
        "audit",
        TestCloudEvents.record(UUID.randomUUID(), deleted, new byte[0]),
        event -> calls.incrementAndGet());

    assertEquals(0, calls.get());
  }

  @Test
  void doesNotRetryARecordItCannotDecode() {
    DataSchema newer = new DataSchema(WIDGET_CREATED, 3);
    ConsumerRecord<String, byte[]> record =
        TestCloudEvents.record(UUID.randomUUID(), newer, new byte[0]);
    AtomicInteger calls = new AtomicInteger();

    assertThrows(
        IllegalStateException.class,
        () -> consumer.handle("audit", record, event -> calls.incrementAndGet()));
    assertEquals(0, calls.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Audit", "audit.log", "1audit", "audit_log"})
  void rejectsAConsumerNameThatCannotNameADeadLetterTopic(String name) {
    ConsumerRecord<String, byte[]> record = widgetCreated(UUID.randomUUID());

    assertThrows(IllegalArgumentException.class, () -> consumer.handle(name, record, event -> {}));
  }

  @Test
  void deletesInboxEntriesOlderThanFourteenDays() {
    Instant cutoff = TestClockProducer.NOW.minus(Duration.ofDays(14));
    InboxEventEntity.Key expired = insert(cutoff.minusSeconds(1));
    InboxEventEntity.Key retained = insert(cutoff.plusSeconds(1));

    consumer.deleteExpiredInboxEntries();

    QuarkusTransaction.requiringNew()
        .run(() -> {
          assertFalse(inbox.findByIdOptional(expired).isPresent());
          assertTrue(inbox.findByIdOptional(retained).isPresent());
        });
  }

  private boolean transactionActive() {
    try {
      return transactions.getStatus() == Status.STATUS_ACTIVE;
    } catch (SystemException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ConsumerRecord<String, byte[]> widgetCreated(UUID id) {
    return TestCloudEvents.record(
        id,
        new DataSchema(WIDGET_CREATED, 2),
        TestCloudEvents.encode(new WidgetCreated("w-1", null), WidgetCreated.getClassSchema()));
  }

  private boolean inboxed(String consumerName, UUID eventId) {
    return QuarkusTransaction.requiringNew()
        .call(() ->
            inbox.findByIdOptional(new InboxEventEntity.Key(consumerName, eventId)).isPresent());
  }

  private InboxEventEntity.Key insert(Instant processedAt) {
    InboxEventEntity entry = new InboxEventEntity();
    entry.key = new InboxEventEntity.Key("cleanup", UUID.randomUUID());
    entry.processedAt = processedAt;
    QuarkusTransaction.requiringNew().run(() -> inbox.persist(entry));
    return entry.key;
  }
}
