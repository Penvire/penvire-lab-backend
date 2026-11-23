package com.penvire.lab.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Reads what tests sent to the Dev Services broker. */
@Singleton
public class TestKafka {

  private final Map<String, Object> config;

  TestKafka(@Identifier("default-kafka-broker") Map<String, Object> config) {
    this.config = config;
  }

  /**
   * Reads the topic from the start, in a consumer group of its own, until {@code count} records
   * arrived.
   *
   * @throws AssertionError if fewer arrive within 20 seconds
   */
  public List<ConsumerRecord<String, byte[]>> consume(String topic, int count) {
    Map<String, Object> consumerConfig = new HashMap<>(config);
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    try (var consumer =
        new KafkaConsumer<>(
            consumerConfig, new StringDeserializer(), new ByteArrayDeserializer())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (records.size() < count && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(200)).forEach(records::add);
      }
    }
    assertEquals(count, records.size(), "records on " + topic);
    return records;
  }

  /**
   * The record's last header of that name, as UTF-8.
   *
   * @throws AssertionError if the record has no such header
   */
  public static String header(ConsumerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    assertNotNull(header, "header " + name);
    return new String(header.value(), UTF_8);
  }
}
