---
status: accepted
date: 2026-11-27
---

# Fine-grained authorization with OpenFGA

Services need relationship-based authorization across tenants, engagements, evidence, custom apps, API tokens and platform administration. Role claims inside JWTs cannot express resource-level relationships and remain stale until token expiry, while separate per-service SQL role tables and authorization caches duplicate logic and drift across services. Both the lab and the product therefore adopt **OpenFGA** (PostgreSQL-backed in production) as the shared fine-grained authorization engine and source of truth for authorization relationships, accessed by every service through a shared simplification library (`libs/fga`) that wraps `dev.openfga:openfga-sdk`.

## Decision

- **OpenFGA is the source of truth for authorization relationships.** Permissions, roles and resource-access relationships live in OpenFGA rather than being mirrored in service SQL tables. Services write and delete tuples directly and synchronously through `libs/fga`, giving immediate read-your-own-writes and immediate revocation well within the 5-second revocation target.
- **Subjects use the JWT `sub` directly, separated by principal type.** Human users appear in OpenFGA as `user:<sub>` and autonomous clients or services (`client_credentials`) as `client:<client_id>`. This supersedes the separate `(iss, sub)`-to-`principalId` mapping in `docs/jwt-security-handout.md`: Audit ([ADR 0006](0006-audit-events-recorded-by-each-service.md)) and Identity already key on `sub`, Identity (or Keycloak in production) brokers external identity providers under one issuer, and Keycloak preserves user UUIDs during migration via `partial-import` or `kc.sh import`.
- **Each service enforces its own authorization and maintains its own tuples.** Services never trust edge headers; each checks the required relation in OpenFGA for its operations and maintains structural tuples linking its entities to parent objects (for example `engagement:<id>#tenant -> tenant:<id>`). Identity uses OpenFGA for its management operations (such as platform-admin account actions, and later custom apps, API tokens and consent), not for core OIDC protocol flows, invite redemption, self-service password changes or break-glass signing-key revocation.
- **Fail closed on outages and integrate with auditing.** If OpenFGA is unreachable, times out or returns a server error, `libs/fga` throws `FgaUnavailableException` so protected endpoints return `503 Service Unavailable` and never fail open. Checks default to `HIGHER_CONSISTENCY` so revocations take effect immediately. Authorization refusals via `require(...)` throw Quarkus `ForbiddenException`, which `@Audited` actions record as `DENIED` automatically ([ADR 0008](0008-audited-actions-choose-recorded-failures.md)).
- **One shared simplification library `libs/fga` wraps `openfga/java-sdk`.** Services do not use the generated SDK request/response classes or `CompletableFuture` API directly. `libs/fga` exposes a small, synchronous, `@NullMarked` CDI facade (`FgaClient`) with validated value types (`FgaSubject`, `FgaObject`, `FgaTuple`) that prevent tuple-string injection, single checks, `require(...)`, idempotent writes and deletes (`onDuplicate = IGNORE`, `onMissing = IGNORE`), atomic write-and-delete updates, paginated `read`, bounded `listObjects`, and in-memory test fixtures for unit tests.
- **PostgreSQL in production; SQLite in local `devenv`.** Production runs OpenFGA on PostgreSQL. Local `devenv` runs OpenFGA backed by SQLite under `$DEVENV_STATE/openfga` on `127.0.0.1:8090` (HTTP), `127.0.0.1:8091` (gRPC) and `127.0.0.1:3001` (Playground), exporting OpenTelemetry traces to the local collector on `127.0.0.1:4317`, with `fga` (`openfga-cli`) available in the shell.

## Considered options

- **Tenant-owned SQL role tables with per-service authorization caches** (sketched earlier in `docs/jwt-security-handout.md`). Rejected: every service would have to build resource-level authorization rules and bounded invalidation caches, and cross-resource hierarchies do not fit flat tenant roles.
- **Separate application `principalId` mapped from `(iss, sub)` in Tenant.** Rejected: every request to every service would pay a lookup and cache hop before calling OpenFGA, Identity would gain a circular dependency on Tenant, and Audit (`sub`) and authorization (`principalId`) would split user identity across two IDs.
- **Syncing OpenFGA tuples asynchronously through the transactional outbox and Kafka.** Rejected: consumer lag or failures delay revocation past the 5-second requirement and break read-your-own-writes immediately after creating a resource.
- **Using `openfga/java-sdk` directly in each service, or a third-party Quarkus extension.** Rejected: calling the raw SDK repeats `CompletableFuture` handling, stringly-typed `type:id` formatting, error translation and idempotency options in every service; third-party Quarkus extensions lag Quarkus upgrades and reduce control over the SDK.

## Consequences

- OpenFGA is tier-0 infrastructure in production: protected requests fail with `503` while it is unavailable, so it must run replicated on PostgreSQL and be monitored through OpenTelemetry.
- Entity creation and deletion in services make a synchronous OpenFGA tuple call alongside the service's database work. Revocations and deletions should remove tuples fail-safely so stale access never outlives a committed delete.
- Migrating in the future to a hosted IdP that cannot preserve existing `sub` UUIDs would require either a custom token claim or a one-time tuple migration; this is accepted in exchange for eliminating per-request principal translation.
- Services remain free to add short-lived caching later only if measured latency requires it and revocation freshness stays within 5 seconds.
