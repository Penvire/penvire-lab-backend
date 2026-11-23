---
status: accepted, partly superseded by ADR-0008
date: 2026-11-23
---

# Audit events recorded by each service through its outbox, beside its domain events

[ADR 0004](0004-events-written-with-the-latest-revision-of-their-package.md) expects Audit to be the first service that reacts to domain events. Domain events cannot carry an audit trail. A denied or failed action changes no state, so it writes no domain event. A domain event describes the change, not who caused it. Reads change nothing, and some of them must be audited too. So every service records **audit events** of its own, through the same outbox and in the same transaction as the action, next to its domain events. Services record them from their first security-relevant use case, so that no producer has to be revisited when the Audit service is built. Building that service is not part of this decision. ADR 0004 stays in force; only its expectation that Audit consumes domain events changes.

## Decision

- **One record per service, one shape for all.** Each service's event package contains an `AuditRecorded` record (`dev.penvire.lab.<service>.events.v1.AuditRecorded`). The CloudEvents `source` and `type` therefore name the service that recorded it, as ADR 0004 requires. All services use the same fields, and contracts CI rejects an `AuditRecorded` that differs from the shared definition.

  | Field | Type | Meaning |
  | --- | --- | --- |
  | `action` | string | `<service>.<resource>.<verb>`, for example `identity.access-token.issue` |
  | `outcome` | enum `SUCCESS`, `DENIED`, `FAILED` | `DENIED`: refused by an authorization or validation rule. `FAILED`: an error stopped it. |
  | `actorType` | enum `USER`, `CLIENT`, `SYSTEM`, `ANONYMOUS` | `CLIENT`: a client acting for itself, including another service. `SYSTEM`: the service on its own, for example a scheduled job. `ANONYMOUS`: the caller is not authenticated. |
  | `actorId` | nullable string | the user's `sub` or the client's `client_id`; null for `SYSTEM` and `ANONYMOUS` |
  | `clientId` | nullable string | the client a user acted through |
  | `targetType` | string | the kind of resource acted on, for example `access-token` |
  | `targetId` | nullable string | the resource's ID; null when an action was refused before a resource existed |
  | `reason` | nullable string | a machine-readable reason code for `DENIED` and `FAILED`, for example `invalid_client` |
  | `changes` | array of `fieldPath`, nullable `oldValue`, nullable `newValue`; default empty | the fields the action changed; values are JSON text, and null means not recorded |

  Tenant-scoped actions set the `tenantid` CloudEvents attribute (ADR 0004). An action without one is platform-scoped. The time is the CloudEvents `time`, so the payload has no timestamp.
- **All changes recorded with values, unless excluded.** An audit trail must say what changed, so by default every changed field is recorded with its old and new value. The log is meant to be immutable, though, and must not collect personal data it can never erase. So a service excludes what must not be recorded: personal fields such as an email by path alone, so the log shows that it changed but not to what, and credentials, secrets, tokens or a whole personal profile not at all. Free text appears nowhere in the record outside `changes`. How the Audit service later pseudonymizes or erases actor IDs is decided in the Audit track.
- **Topic `<service>.audit`.** Each service publishes to its own audit topic, for example `identity.audit`. The record key and `subject` are `targetId`, or the actor ID when there is no target, so the events about one resource stay in order. When there is neither, for example an anonymous caller refused before a resource existed, they are the event `id`: such an event has nothing to stay in order with. This is the one exception to the rule of one topic per aggregate type.
- **Same transaction for successful actions, a separate one for refusals and failures.** A successful action records its audit event in the transaction that stores the change, like any other outbox event. A refused or failed action rolls that transaction back, which would also remove its audit event. So `DENIED` and `FAILED` are recorded in a new transaction after the use case gives up.
- **A shared `libs/audit` does the recording; use cases declare what they do.** Audit events are written only through it, never through `EventPublisher` directly. It takes over everything a use case would otherwise repeat:
  - **Declared on the use case.** A use case method is annotated with its action and, if it changes state, the type of that state, for example `@Audited(value = "identity.client.update", state = Client.class)`. The library fails the action if the state the use case reports is of another type. An interceptor runs inside the use case's transaction. When the method returns, it records `SUCCESS` in that transaction. When the method throws, it records `DENIED` or `FAILED` in a new transaction, after the use case's transaction has rolled back. Sensitive reads use the same annotation and record `SUCCESS` with no changes.
  - **Outcome and reason from the exception.** An exception that declares a reason code and whether it is a refusal gives `DENIED` or `FAILED` with that code. Any other exception gives `FAILED` with the reason `error`. The exception's message is never recorded.
  - **Actor and tenant from the request.** The library takes `actorType`, `actorId` and `clientId` from the request's authenticated caller, and the `tenantid` attribute from the request's tenant. Without a caller it records `ANONYMOUS`. Outside a request, for example in a scheduled job or an event consumer, it records `SYSTEM`. A use case that authenticates the caller itself, such as Identity's token endpoint, sets the actor explicitly.
  - **Target and changes reported by the use case.** While it runs, the use case reports its target and the state before and after the change to a request-scoped audit scope. The library compares the two and builds `changes` itself. Nested fields use dotted paths. A collection is compared as one value. Each changed field is handled in one of three ways:
    - **Recorded with values**: the default for every changed field.
    - **Recorded by path alone**, with both values null, for fields the action lists: `@Audited(value = "identity.user.update", pathOnly = {"email"})`.
    - **Ignored**, not recorded at all, not even its path. The action can list fields (`ignore = {"secretHash"}`) or whole types (`ignoreTypes = {Profile.class}`). An ignored type is skipped wherever it appears, including as a nested field. If the state itself is of an ignored type, the action records no changes. A type or field annotated `@AuditIgnored` (for example a credential or a personal profile) is ignored by every action, so it stays private without being repeated on each use case. Ignoring wins over `pathOnly`.
- **A generated audit manifest shows every change in review.** A Gradle task reads the `@Audited` methods, their state types and `@AuditIgnored` from the Jandex index and writes `services/<service>/audit-manifest.yaml`: for each action, every field path and whether it is recorded with values, by path alone or ignored. The file is generated and committed, never edited by hand. `./gradlew build` regenerates it, and CI fails if the committed file is out of date. On every pull request, CI compares the manifest with the base branch and posts a comment listing the actions and fields that were added or removed, and every field that now records its values. That comment is where a reviewer decides whether a new value may enter the immutable log.
- **Recording is never optional.** An `@Audited` method fails if the audit event cannot be written, and the action is rolled back with it. Recording a refusal or failure that cannot be written is logged as an error, and the original exception is rethrown.
- **Security-relevant actions only.** A service records an audit event for sign-in, token issuance and refusals, changes to permissions, memberships, keys and clients, deletions, and reads of sensitive data such as evidence and the audit log. Routine reads and internal bookkeeping are not audited.

## Considered options

- **Audit derives its trail from domain events**, as ADR 0004 expects. Rejected: refusals, actors and reads have no domain event, and Audit would have to translate every event type of every service.
- **One shared `dev.penvire.lab.audit.events.v1.AuditEvent` on one `audit.event` topic**, as in the earlier lab. It gives one schema and one topic to read. Rejected: `libs/event` derives `source` and `type` from the schema namespace, so every event would claim to come from Audit, and no service would own the topic. Taking `source` from configuration instead would weaken that rule for every event.
- **Each service calls the Audit service synchronously.** Rejected: the call and the database write are not atomic, and every action would fail while Audit is down.
- **An explicit recorder called by each use case**, with actor, target, outcome and changes passed in. It is simpler and easier to follow, but every use case repeats the same code, and a forgotten call on an error path loses exactly the refusals the trail is for.
- **Hibernate Envers for the changes.** It versions entities automatically, but it stores history in each service's own tables, knows nothing about actors, refusals or reads, and records every column, including personal ones.
- **Computing the manifest in CI only**, for the base branch and the pull request, without committing it. It saves a generated file, but CI has to build the base branch on every pull request, and there is no history of what was audited when.
- **Structured logs to Loki.** Rejected: logs are sampled, rotated and have no ordering or delivery guarantee, so they cannot be an audit trail.
- **No changed values at all**, only identifiers. It keeps personal data out by construction, but "changed member Y's role" without "from `member` to `admin`" is of little use, and the values would have to be pieced together from domain events, which refusals and reads do not have.
- **Values only for allowlisted fields**, every other field by path alone. A personal field added later could leak its path but never its value. Rejected: every action would have to list every field worth auditing, and a forgotten entry silently drops the value the trail was for. Recording everything by default and excluding the private fields keeps the trail complete; the cost is the consequence below.
- **Encrypting each subject's values with a key deleted on erasure** (crypto-shredding) instead of excluding them. It would allow recording personal values too, but it needs a key store and key management before the first producer can record anything. Exclusions leave it open for later.

## Consequences

- No service consumes the audit topics yet. Events older than the topic retention are lost until the Audit service exists, so it starts with a gap.
- Contracts gains a shared `AuditRecorded` definition and a CI check that keeps every service's copy identical. A change to the shape is a revision in every service package at once.
- A successful action that also changes state writes two outbox rows: its domain event and its audit event.
- A service records audit events only once it has a database and `libs/event`. For Identity, that comes first.
- `libs/audit` depends on the request's security context and on CDI interceptor ordering: its interceptor must run inside `@Transactional`, or a success would be recorded outside the action's transaction. Its tests must cover that ordering.
- A field added to an audited state is recorded with its values until someone excludes it. A personal field that is added without `@AuditIgnored` or a `pathOnly` entry lands in the immutable log, and nothing can remove it there. The manifest comment is the guard: it names every such field in the pull request that adds it.
- What is recorded is hidden behind annotations; the manifest makes it visible. A state whose type is decided only at runtime, such as an interface with several implementations, cannot be listed in the manifest, so the Gradle task rejects it.
- The PR comment needs a workflow with permission to comment on pull requests. Pull requests from forks get no comment, which does not matter while the maintainer is the only contributor.
