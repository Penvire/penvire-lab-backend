/**
 * Event types and schema revisions shared by producers and consumers (ADR 0004).
 *
 * <p>Services use {@link com.penvire.lab.event.outbox.EventPublisher} to publish and {@link
 * com.penvire.lab.event.inbox.EventConsumer} to consume. The types here are public only because
 * both packages need them.
 *
 * <p>Event classes come from the {@code <service>-events} contract packages: classes generated from
 * the latest schema revision, plus every revision as {@code avro/<full name>/<n>.avsc}. The
 * CloudEvents {@code type} and {@code source} derive from the schema's full name {@code
 * dev.penvire.lab.<service>.events.v<major>.<RecordName>}, by the same rule contracts CI checks.
 * Classes in such packages are trusted by Avro's class security check; no others are added.
 */
@NullMarked
package com.penvire.lab.event;

import org.jspecify.annotations.NullMarked;
