package com.penvire.lab.event.outbox;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Sends outbox rows to Kafka and marks them published (ADR 0004). Delivery is at least once: a
 * crash after sending but before commit sends the row again, and consumers deduplicate by event ID.
 */
@Singleton // Not @ApplicationScoped: the constructor creates the producer and must run EXACTLY
// once.
class OutboxRelay {

  private static final int BATCH_SIZE = 100;
  private static final Duration RETENTION = Duration.ofDays(7);

  private final OutboxEventRepository outbox;
  private final Clock clock;
  private final Producer<String, byte[]> producer;

  OutboxRelay(
      OutboxEventRepository outbox,
      Clock clock,
      @Identifier("default-kafka-broker") Map<String, Object> kafkaConfig) {
    this.outbox = outbox;
    this.clock = clock;

    Map<String, Object> config = new HashMap<>(kafkaConfig);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    // A send gives up within 40 s (metadata wait plus delivery), before the relay's transaction
    // times out at 60 s, so an unreachable broker ends the run instead of rolling it back.
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30_000);
    this.producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer());
  }

  /**
   * Sends one row at a time and stops at the first failure, so a later event of the same aggregate
   * is never sent before an earlier one. Rows marked so far commit; the rest wait for the next run.
   */
  @Scheduled(
      every = "${penvire.event.relay.interval:1s}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  @Transactional
  void relay() {
    if (!outbox.tryBecomeRelay()) {
      return;
    }

    for (OutboxEventEntity row : outbox.lockUnpublished(BATCH_SIZE)) {
      try {
        producer.send(OutboxRecords.toRecord(row)).get();
      } catch (ExecutionException e) {
        Log.warnf(
            e.getCause(), "Could not send event %s to %s; retrying next run", row.id, row.topic);
        return;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }

      row.publishedAt = clock.instant();
    }
  }

  @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  @Transactional
  void deletePublished() {
    outbox.deletePublishedBefore(clock.instant().minus(RETENTION));
  }

  @PreDestroy
  void releaseRelay() {
    producer.close(Duration.ofSeconds(5));
  }
}
