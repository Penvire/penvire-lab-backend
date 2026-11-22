---
status: proposed
date: 2026-11-22
---

# Events through a transactional outbox, written with the latest schema revision of their package

Supersedes [ADR 0003](0003-events-through-a-transactional-outbox.md). Only two things change: how a producer chooses the schema revision it writes, and a rule that keeps logical types stable. A configured writer revision cannot work with generated classes. Avro writes a generated record by field position, so a class built from a later revision cannot produce an older revision once a field was dropped or inserted.

Services publish their state changes as events so that other services (Audit first) can react without calling them. Writing to the database and to Kafka from the same use case can leave one write done and the other lost. So each service writes its events to an outbox table in the same transaction as the change, and a relay publishes them. Events travel as CloudEvents in binary mode with Avro payloads whose schemas come from the published contract packages, like every other contract.

## Decision

- **Transactional outbox.** A use case appends its events to the service's `outbox_event` table in the same transaction that stores the aggregate. Like `DomainRepository`, the outbox writer requires the caller's transaction. Use cases never write to Kafka directly. A relay in each service polls unpublished rows with `FOR UPDATE SKIP LOCKED`, sends them and marks them published. Published rows are deleted after 7 days.
- **At-least-once delivery, idempotent consumers.** The relay can send an event more than once (for example, when it crashes after sending but before marking the row). Every consumer keeps an `inbox_event` table keyed by consumer name and event ID. It handles the event and records it in one transaction, and it skips events it has already recorded. A consumer retries a failed event a bounded number of times, then moves it to its dead-letter topic `<topic>.<consumer>.dlt`.
- **CloudEvents 1.0, Kafka protocol binding, binary content mode.** The attributes travel as `ce_` headers. The record value is the Avro-encoded payload alone, so routing, filtering and tracing never need to decode the payload.

  | Attribute | Value |
  | --- | --- |
  | `id` | UUIDv7 from the service's `IdGenerator` |
  | `source` | `/penvire-lab/<service>` |
  | `type` | `dev.penvire.lab.<service>.<event>.v<major>` |
  | `subject` | the aggregate ID |
  | `time` | the service's `Clock` |
  | `datacontenttype` | `application/avro` |
  | `dataschema` | `urn:penvire-lab:schema:<avro full name>:<revision>` |
  | `traceparent` | W3C trace context (CloudEvents distributed tracing extension) |
  | `tenantid` | the owning tenant, for tenant-scoped events |

- **Avro schemas from the contract packages, no schema registry.** Schemas live in penvire-lab-contracts under `asyncapi/<service>/`, next to the service's AsyncAPI document, and reach services only as published packages ([ADR 0001](0001-versioned-contracts-through-published-packages.md)). Every schema change gets a new revision number, and the package ships every revision, not only the latest. A consumer looks up the writer schema by the event's `dataschema` and resolves it against its own reader schema.
- **Producers write the latest revision of the package they depend on.** The producer's dependency on its own `<service>-events` package is the pin: it writes the latest revision in that version, using the generated classes. It raises the dependency only after every consumer depends on a version that contains the new revision. A consumer that meets an unknown revision treats it as a failure: retry, then dead-letter.
- **Compatibility: backward transitive.** Each revision must be readable by a reader on any later revision. Contracts CI checks this the same way it checks OpenAPI breaking changes. Because producers raise their dependency only after consumers have, consumers never read with an older schema than the writer's, so forward compatibility is not required. Avro's check compares encoded types only, so contracts CI also rejects a value whose logical type (or decimal precision and scale) changes between revisions: `timestamp-millis` to `timestamp-micros` is "compatible" to Avro but changes every value. An incompatible change is a new type major (`….v2`) on a new topic. Producers keep publishing the old major until its consumers have moved.
- **One topic per aggregate type.** Topics are named `<service>.<aggregate>`, for example `identity.access-token`. The record key is the aggregate ID, so the events of one aggregate stay in order.

## Considered options

- **Dual write from the use case** (database, then Kafka). Rejected: the two writes are not atomic, so a crash between them loses or invents an event.
- **Change data capture with Debezium instead of a polling relay.** It avoids polling and reads changes from the WAL. Rejected for now: it needs Kafka Connect and Debezium in every environment, which is heavy at lab scale. The outbox table stays the same, so switching later only replaces the relay.
- **A schema registry (Apicurio).** Standard Avro serdes, and new writer schemas are fetched at runtime. Rejected: it would be a second source of truth beside the contract packages and another piece of infrastructure. Producer dependencies and consumer-first upgrades solve the same problem.
- **A configured writer revision** (ADR 0003). Rejected: generated classes match only the latest revision, and Avro writes them by field position. Writing an older revision would need a field-by-field conversion of every record, including nested ones, in `libs/events`.
- **Structured content mode** (the whole envelope as one record). Rejected: every hop would have to decode the payload just to read the attributes.
- **One topic per event type, or one per service.** Per event type loses ordering across the events of one aggregate. Per service makes every consumer read and filter everything.

## Consequences

- Upgrade order: every consumer must deploy a `<service>-events` version containing a new schema revision before the producer raises its own dependency to that version. Revisions that are ahead of some consumer fail on that consumer until it upgrades.
- Contracts gains a second pipeline: AsyncAPI and Avro schemas per service, Java code generated from them, all revisions kept in the package, and a backward-transitive compatibility check in CI. That work comes before `libs/events` can use Avro.
- Every service that publishes or consumes events needs Kafka, provided by Quarkus Dev Services in dev mode and tests. Local devenv adds a broker once two services exchange events.
- Every service that publishes events gets an `outbox_event` table, and every service that consumes them gets an `inbox_event` table, through its own Flyway migrations.
