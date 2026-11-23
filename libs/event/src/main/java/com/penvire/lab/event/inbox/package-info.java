/**
 * Consuming each event once per consumer through an inbox, with retries and dead-lettering (ADR
 * 0004, ADR 0005).
 *
 * <p>A service consumes a topic through a SmallRye Kafka channel and hands each record to {@link
 * com.penvire.lab.event.inbox.EventConsumer}. The method must be {@code @Blocking}, and must stay
 * ordered (the default), so the events of one aggregate are handled in order:
 *
 * <pre>{@code
 * @Incoming("identity-session")
 * @Blocking
 * void consume(ConsumerRecord<String, byte[]> record) {
 *   events.handle("audit", record, event -> {
 *     switch (event.payload()) {
 *       case SessionStarted started -> auditLog.sessionStarted(event, started);
 *       default -> {}
 *     }
 *   });
 * }
 * }</pre>
 *
 * <p>The channel reads raw bytes and dead-letters to {@code <topic>.<consumer>.dlt}. Using the
 * consumer name as the Kafka consumer group keeps the two aligned:
 *
 * <pre>
 * mp.messaging.incoming.identity-session.connector=smallrye-kafka
 * mp.messaging.incoming.identity-session.topic=identity.session
 * mp.messaging.incoming.identity-session.group.id=audit
 * mp.messaging.incoming.identity-session.value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
 * mp.messaging.incoming.identity-session.failure-strategy=dead-letter-queue
 * mp.messaging.incoming.identity-session.dead-letter-queue.topic=identity.session.audit.dlt
 * </pre>
 *
 * <p>For each record the consumer:
 *
 * <ul>
 *   <li>decodes the payload with the revision named by {@code ce_dataschema}, resolved against the
 *       class in the consumer's contract package. A type without a class there is skipped: a
 *       consumer only depends on the types it needs. A revision the package lacks fails: the
 *       consumer must upgrade its package before the producer writes that revision;
 *   <li>records the event in {@code inbox_event} and runs the handler in one transaction, so a
 *       redelivered event is handled once per consumer, and a failed handler leaves no entry. A
 *       retried handler runs again, so its side effects outside the transaction must be idempotent;
 *   <li>retries a failing handler {@code penvire.event.consumer.attempts} times (default 3), each
 *       attempt in a new transaction, doubling {@code penvire.event.consumer.backoff} (default
 *       {@code PT1S}) in between. Then it rethrows, and the channel dead-letters the record.
 *       Decoding failures are not retried; they cannot succeed on a second attempt.
 * </ul>
 *
 * <p>A dead-lettered event no longer holds up the aggregate's later events, so their order is
 * broken from that point on; the dead-letter topic keeps the event and its headers for replay.
 * Inbox entries are deleted after 14 days, twice Kafka's default retention.
 */
@NullMarked
package com.penvire.lab.event.inbox;

import org.jspecify.annotations.NullMarked;
