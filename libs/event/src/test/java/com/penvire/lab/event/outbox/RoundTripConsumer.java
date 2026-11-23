package com.penvire.lab.event.outbox;

import com.penvire.lab.event.inbox.EventConsumer;
import com.penvire.lab.event.inbox.IncomingEvent;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/** A consumer wired the way a service wires one; see the channel in application.properties. */
@ApplicationScoped
class RoundTripConsumer {

  /** Events with this subject always fail, so they end up in the dead-letter topic. */
  static final String POISON = "poison";

  private final EventConsumer events;
  private final List<IncomingEvent> handled = new CopyOnWriteArrayList<>();

  RoundTripConsumer(EventConsumer events) {
    this.events = events;
  }

  @Incoming("widget-round-trip")
  @Blocking
  void consume(ConsumerRecord<String, byte[]> record) {
    events.handle(
        "round-trip",
        record,
        event -> {
          if (POISON.equals(event.subject())) {
            throw new IllegalStateException("the handler rejects " + POISON);
          }
          handled.add(event);
        });
  }

  /** A method, not a field: tests reach this bean through its client proxy. */
  List<IncomingEvent> handled() {
    return handled;
  }
}
