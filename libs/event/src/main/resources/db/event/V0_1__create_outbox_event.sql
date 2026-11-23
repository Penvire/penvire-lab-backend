-- Events waiting for the relay (ADR 0004). Each row holds the CloudEvents attributes and the
-- Avro-encoded payload, so the relay sends it without knowing the event type.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(255) NOT NULL,
    type         VARCHAR(255) NOT NULL,
    source       VARCHAR(255) NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    time         TIMESTAMP WITH TIME ZONE NOT NULL,
    dataschema   VARCHAR(255) NOT NULL,
    traceparent  VARCHAR(55),
    tenantid     VARCHAR(255),
    payload      BYTEA NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

-- The relay polls unpublished rows in ID order; UUIDv7 IDs are ordered by creation.
CREATE INDEX outbox_event_unpublished_idx ON outbox_event (id) WHERE published_at IS NULL;

-- Cleanup deletes published rows after 7 days.
CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE published_at IS NOT NULL;
