---
status: accepted
date: 2026-11-23
---

# Event library: one relay per service, SmallRye consumers, and migrations shipped by the library

[ADR 0004](0004-events-written-with-the-latest-revision-of-their-package.md) decides how events travel. Building `libs/event` raised questions it leaves open: how the relay keeps one aggregate's events in order, how consumers attach to Kafka, which incoming events a consumer may ignore, and who owns the outbox and inbox tables. This record answers them. ADR 0004 stays in force. One point is narrowed: an event the consumer cannot decode goes to the dead-letter topic without retries, because a retry cannot succeed.

## Decision

- **One relay per service, one event at a time.** Each run takes a Postgres advisory lock (`pg_try_advisory_xact_lock`) and skips the run if another instance holds it. `FOR UPDATE SKIP LOCKED` alone would let a second relay send an aggregate's later event while the first still holds the earlier one. The relay sends rows in ID order, waits for each acknowledgement, and stops at the first failure, so a later event never overtakes an earlier one that failed. Rows sent so far are marked published in the same run.
- **Trace context in both headers.** Records carry `ce_traceparent`, as the AsyncAPI documents require, and the plain W3C `traceparent`, which is the only header Kafka tracing in SmallRye and OpenTelemetry reads.
- **Consumers are SmallRye Kafka channels.** A service declares an `@Incoming` method, `@Blocking` and ordered, and passes each record to `EventConsumer`. SmallRye owns polling, rebalancing, offset commits and the dead-letter producer (`failure-strategy=dead-letter-queue`, topic `<topic>.<consumer>.dlt`). The library owns decoding, the inbox and retries. The consumer name, `[a-z][a-z0-9-]{0,62}`, is the inbox key and part of the dead-letter topic. It also serves as the channel's `group.id`.
- **Claim first, then handle.** Each attempt runs in a new transaction. The transaction inserts the inbox entry with `ON CONFLICT DO NOTHING` and calls the handler only if the row is new. The primary key settles concurrent deliveries, so there is no check-then-act race. A failed attempt rolls back its entry. Handlers therefore change the database once per event, but they may run several times, and side effects outside the transaction must be idempotent.
- **Bounded retries for handler failures only.** A failing handler is retried `penvire.event.consumer.attempts` times (default 3), with a doubling `penvire.event.consumer.backoff` (default 1 s). After that the exception reaches SmallRye, which dead-letters the record. Decoding failures go straight to the dead-letter topic: an unknown schema revision, a missing CloudEvents header or an unreadable payload. Once the consumer has upgraded its contract package, the event is replayed from there.
- **Types without a class are skipped.** A consumer depends only on the contract packages it needs, and a topic can carry several event types. An event whose schema has no generated class on the consumer's classpath is logged at debug level and skipped. It does not go to the inbox. A known type with an unknown revision still fails.
- **Avro trusts Penvire Lab event packages.** Avro 1.12 instantiates only trusted classes (CVE-2024-47561). The library adds one rule to Avro's global allowlist: classes whose package is an event namespace `dev.penvire.lab.<service>.events.v<major>`. Nested records and enums are included; nothing else is added.
- **The library ships its tables.** `outbox_event` and `inbox_event` are Flyway migrations in the library under `db/event`, with the reserved versions `V0_n`. Service migrations start at `V1`. The library's defaults add `db/event` to `quarkus.flyway.locations` and enable `out-of-order`, so a library upgrade can apply `V0_n` after a service's later migrations. The two sets of tables never reference each other. The entities and their schema change in the same pull request.
- **Retention.** Published outbox rows are deleted after 7 days (ADR 0004). Inbox entries are deleted after 14 days, twice Kafka's default topic retention, after which an event can no longer be redelivered.

## Considered options

- **Our own consumer loop instead of SmallRye.** It gives full control, like the relay. Rejected: we would own polling threads, rebalancing, offset commits after the transaction, shutdown, health checks and tracing. SmallRye already does all of that correctly.
- **SmallRye Fault Tolerance or retry topics for retries.** Fault Tolerance (`@Retry`) needs another extension and keeps the retry policy in every service. Retry topics let later events of an aggregate overtake a failing one. Retrying in place in the library blocks only the failing partition, and only for seconds.
- **Retrying decoding failures**, as ADR 0004's wording reads. Rejected: decoding is deterministic, so retries only delay the dead-letter entry.
- **Failing on types without a class.** Every consumer would have to depend on every type on its topics, and a producer adding a type would push all existing consumers into the dead-letter topic.
- **Service-owned migrations for the library's tables.** Each service would copy the SQL, and the library's entities could drift from it until Hibernate's validation fails at startup.
- **A separate Flyway history for the library.** It avoids out-of-order runs, but it must run before Hibernate validates the schema, which is awkward in Quarkus.

## Consequences

- A dead-lettered event no longer holds up the aggregate's later events. Its order is broken from then on, and replay from the dead-letter topic is a manual step.
- An interrupt during the retry backoff, for example at shutdown, dead-letters the record, even though a later attempt might have succeeded.
- The dead-letter topic name is a convention that each service configures. The library does not check it.
- At most one instance of a service relays at a time. Relay throughput is bounded by one batch of 100 events per run, which is enough at lab scale.
- A service that sets `quarkus.flyway.locations` itself must keep `db/event`. Flyway logs a warning that out-of-order migrations may not be reproducible.
