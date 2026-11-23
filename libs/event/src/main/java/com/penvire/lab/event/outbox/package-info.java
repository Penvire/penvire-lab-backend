/**
 * Publishing events through the transactional outbox and relaying them to Kafka (ADR 0004).
 *
 * <p>A use case publishes inside the transaction that stores its change. The event is a class
 * generated from the service's own {@code <service>-events} contract package; the topic is {@code
 * <service>.<aggregate>} and the subject is the aggregate ID:
 *
 * <pre>{@code
 * @Transactional
 * public void startSession(...) {
 *   sessions.insert(session);
 *   events.publish("identity.session", session.id().toString(), new SessionStarted(...));
 * }
 * }</pre>
 *
 * <p>{@link com.penvire.lab.event.outbox.EventPublisher} writes the event to {@code outbox_event}
 * and fails without a transaction, so an event exists if and only if its change commits. The relay
 * sends unpublished rows every second ({@code penvire.event.relay.interval}) as CloudEvents in
 * Kafka binary content mode, and deletes rows 7 days after sending them. Only one instance of a
 * service relays at a time, and it stops at the first failed send, so the events of one aggregate
 * arrive in order. Delivery is at least once.
 *
 * <p>The library ships the {@code outbox_event} and {@code inbox_event} tables as Flyway migrations
 * in {@code db/event}, with the reserved versions {@code V0_n}. Its defaults add that location and
 * allow out-of-order migrations, so a library upgrade can add {@code V0_n} after a service's own
 * {@code V1} and later. A service that sets {@code quarkus.flyway.locations} itself must keep
 * {@code db/event}.
 */
@NullMarked
package com.penvire.lab.event.outbox;

import org.jspecify.annotations.NullMarked;
