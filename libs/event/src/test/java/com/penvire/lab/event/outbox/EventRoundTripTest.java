package com.penvire.lab.event.outbox;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penvire.lab.event.TestKafka;
import com.penvire.lab.event.inbox.IncomingEvent;
import dev.penvire.lab.widget.events.v1.WidgetCreated;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** Publisher, relay, Kafka and consumer together, as two services would use them. */
@QuarkusTest
class EventRoundTripTest {

  private static final String TOPIC = "widget.round-trip";

  @Inject EventPublisher publisher;

  @Inject OutboxRelay relay;

  @Inject RoundTripConsumer consumer;

  @Inject TestKafka kafka;

  @Test
  void deliversAPublishedEventToItsConsumer() {
    UUID id = publish("w-delivered", new WidgetCreated("w-delivered", "green"));

    relay.relay();

    IncomingEvent event =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(() -> handled(id), Optional::isPresent)
            .orElseThrow();
    assertEquals(new WidgetCreated("w-delivered", "green"), event.payload());
    assertEquals("w-delivered", event.subject());
  }

  @Test
  void deadLettersAnEventItsConsumerKeepsFailingOn() {
    UUID id = publish(RoundTripConsumer.POISON, new WidgetCreated(RoundTripConsumer.POISON, null));

    relay.relay();

    ConsumerRecord<String, byte[]> deadLetter =
        kafka.consume(TOPIC + ".round-trip.dlt", 1).getFirst();
    assertEquals(id.toString(), TestKafka.header(deadLetter, "ce_id"));
    assertEquals(
        "dev.penvire.lab.widget.widget-created.v1", TestKafka.header(deadLetter, "ce_type"));
  }

  private UUID publish(String subject, WidgetCreated event) {
    return QuarkusTransaction.requiringNew().call(() -> publisher.publish(TOPIC, subject, event));
  }

  private Optional<IncomingEvent> handled(UUID id) {
    return consumer.handled().stream().filter(event -> event.id().equals(id)).findFirst();
  }
}
