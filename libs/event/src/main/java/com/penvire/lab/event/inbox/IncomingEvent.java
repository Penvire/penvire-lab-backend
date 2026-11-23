package com.penvire.lab.event.inbox;

import com.penvire.lab.event.DataSchema;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecordBase;
import org.jspecify.annotations.Nullable;

/**
 * An event as a consumer receives it: its CloudEvents attributes and the payload, read into the
 * consumer's generated class. Handlers switch on a payload's type.
 */
public record IncomingEvent(
    UUID id,
    String source,
    String type,
    String subject,
    Instant time,
    DataSchema dataSchema,
    @Nullable String tenantId,
    @Nullable String traceparent,
    SpecificRecordBase payload) {}
