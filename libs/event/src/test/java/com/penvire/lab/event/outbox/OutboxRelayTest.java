package com.penvire.lab.event.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penvire.lab.event.TestClockProducer;
import com.penvire.lab.event.TestEvents.WidgetCreated;
import com.penvire.lab.persistence.IdGenerator;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OutboxRelayTest {

  @Inject OutboxRelay relay;

  @Inject EventPublisher publisher;

  @Inject OutboxEventRepository outbox;

  @Inject IdGenerator ids;

  @Inject
  @Identifier("default-kafka-broker")
  Map<String, Object> kafkaConfig;

  @Test
  void sendsEventsAsCloudEventsAndMarksThemPublished() {
    UUID id = publish("widget.sent", "w-1");

    relay.relay();

    ConsumerRecord<String, byte[]> record = consume("widget.sent", 1).getFirst();
    assertEquals("w-1", record.key());
    assertEquals(id.toString(), header(record, "ce_id"));
    assertEquals("dev.penvire.lab.widget.widget-created.v1", header(record, "ce_type"));
    assertEquals("application/avro", header(record, "content-type"));
    assertNotNull(find(id).publishedAt);
  }

  @Test
  void sendsTheEventsOfOneAggregateInOrder() {
    List<String> published =
        List.of(
            publish("widget.ordered", "w-2").toString(),
            publish("widget.ordered", "w-2").toString(),
            publish("widget.ordered", "w-2").toString());

    relay.relay();

    List<String> received =
        consume("widget.ordered", 3).stream().map(record -> header(record, "ce_id")).toList();
    assertEquals(published, received);
  }

  @Test
  void skipsTheRunWhileAnotherInstanceRelays() throws InterruptedException {
    UUID id = publish("widget.skipped", "w-3");
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean otherIsRelay = new AtomicBoolean();
    Thread otherInstance =
        new Thread(() ->
            QuarkusTransaction.requiringNew()
                .run(() -> {
                  otherIsRelay.set(outbox.tryBecomeRelay());
                  locked.countDown();
                  awaitQuietly(release);
                }));
    otherInstance.start();
    try {
      assertTrue(locked.await(10, TimeUnit.SECONDS), "the other instance took the lock");
      assertTrue(otherIsRelay.get());

      relay.relay();

      assertNull(find(id).publishedAt);
    } finally {
      release.countDown();
      otherInstance.join();
    }
  }

  @Test
  void deletesEventsPublishedMoreThanSevenDaysAgo() {
    UUID expired = insertPublished(TestClockProducer.NOW.minus(Duration.ofDays(7)).minusSeconds(1));
    UUID retained = insertPublished(TestClockProducer.NOW.minus(Duration.ofDays(7)).plusSeconds(1));
    UUID unpublished = publish("widget.cleanup", "w-4");

    relay.deletePublished();

    QuarkusTransaction.requiringNew()
        .run(() -> {
          assertFalse(outbox.findByIdOptional(expired).isPresent());
          assertTrue(outbox.findByIdOptional(retained).isPresent());
          assertTrue(outbox.findByIdOptional(unpublished).isPresent());
        });
  }

  private UUID publish(String topic, String subject) {
    return QuarkusTransaction.requiringNew()
        .call(() -> publisher.publish(topic, subject, new WidgetCreated(subject, null)));
  }

  private UUID insertPublished(java.time.Instant publishedAt) {
    OutboxEventEntity row = new OutboxEventEntity();
    row.id = ids.next();
    row.topic = "widget.cleanup";
    row.type = "dev.penvire.lab.widget.widget-created.v1";
    row.source = "/penvire-lab/widget";
    row.subject = "w-cleanup";
    row.time = publishedAt;
    row.dataschema = "urn:penvire-lab:schema:dev.penvire.lab.widget.events.v1.WidgetCreated:2";
    row.payload = new byte[0];
    row.publishedAt = publishedAt;
    QuarkusTransaction.requiringNew().run(() -> outbox.persist(row));
    return row.id;
  }

  private OutboxEventEntity find(UUID id) {
    return QuarkusTransaction.requiringNew().call(() -> outbox.findById(id));
  }

  /** Reads the topic from the start until {@code count} records arrived, for up to 10 seconds. */
  private List<ConsumerRecord<String, byte[]>> consume(String topic, int count) {
    Map<String, Object> config = new HashMap<>(kafkaConfig);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-relay-test-" + UUID.randomUUID());
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    try (var consumer =
        new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (records.size() < count && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(200)).forEach(records::add);
      }
    }
    assertEquals(count, records.size(), "records on " + topic);
    return records;
  }

  private static String header(ConsumerRecord<String, byte[]> record, String name) {
    return new String(record.headers().lastHeader(name).value(), UTF_8);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
