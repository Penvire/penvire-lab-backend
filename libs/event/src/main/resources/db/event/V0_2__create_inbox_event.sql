-- Events a consumer has handled (ADR 0004). Recording one in the transaction that handles it makes
-- a redelivery a no-op: the primary key admits each event once per consumer.
CREATE TABLE inbox_event (
    consumer        VARCHAR(63) NOT NULL,
    event_id        UUID NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

-- Cleanup deletes entries once Kafka can no longer redeliver their event.
CREATE INDEX inbox_event_processed_at_idx ON inbox_event (processed_at);