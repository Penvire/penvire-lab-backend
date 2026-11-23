---
status: accepted
date: 2026-11-23
---

# Audited actions choose which failures they record, through two interceptors

[ADR 0006](0006-audit-events-recorded-by-each-service.md) records every failure of an audited action, takes `DENIED` or `FAILED` from the exception, and runs one interceptor inside the use case's transaction. Building `libs/audit` showed two problems. Not every failure belongs in an immutable log: an error that stops an action is an operational problem that logs and traces already capture in more detail, and a user whose profile update fails validation leaves an audit event that says nothing about security. And the exception cannot always say whether it is a refusal: Quarkus' `ForbiddenException` from `@RolesAllowed` does not implement our interface. Worse, Quarkus checks `@RolesAllowed` in an interceptor at priority 150, outside `@Transactional` at 200, so an interceptor inside the transaction never sees those refusals at all. So each audited action now lists which exceptions it records as `DENIED` and which as `FAILED`. By default it records the refusals of Quarkus security and no errors. The library records through two interceptors: one outside security checks and the transaction for failures, one inside the transaction for successes. The rest of ADR 0006 stays in force.

## Decision

- **The action lists its refusals and failures.** `@Audited` gains two lists of exception types:

  ```java
  @Audited(
      value = "identity.signing-key.rotate",
      failedOn = KeyRotationException.class)
  ```

  - An exception that is an instance of a type in `deniedOn` is recorded as `DENIED`; subclasses match.
  - Otherwise, one that is an instance of a type in `failedOn` is recorded as `FAILED`.
  - An exception that matches neither is not recorded, and is rethrown unchanged.
  - The defaults are `deniedOn = {ForbiddenException.class, UnauthorizedException.class}` and `failedOn = {}`. An action that lists nothing records every refusal by Quarkus security, from `@RolesAllowed`, `@PermissionsAllowed` or an unauthenticated caller, and no errors. A service's own authorization exceptions that extend `ForbiddenException` are covered without being listed. Listing exceptions replaces a default; it does not add to it.
  - Errors are recorded only where an action lists them, because they are security-relevant there, for example a signing key that could not be rotated.
  - A type listed in both is a programming error. The call fails before the use case runs, so the mistake shows on the first call, successful or not.
- **The exception supplies only the reason code.** An exception implementing `AuditReason` gives its `auditReason()`; any other gives `error`. Whether it is a refusal is no longer the exception's decision, so `AuditReason` has no `isRefusal()`. The exception's message is still never recorded.
- **Two interceptors, each where it can see what it records.**

  ```text
  AuditInterceptor         (PLATFORM_BEFORE + 100)  opens the frame; records DENIED and FAILED
    @RolesAllowed and other security checks (150)
      @Transactional                         (200)
        AuditSuccessInterceptor (LIBRARY_BEFORE) records SUCCESS
          use case
  ```

  - The outer interceptor runs outside security checks and the transaction. It sees refusals by `@RolesAllowed`, and it records a failure in a new transaction after the action's transaction has rolled back, as ADR 0006 describes.
  - The inner interceptor runs inside the transaction and records `SUCCESS` there, so the record commits or rolls back with the change.
  - If recording `SUCCESS` fails, for example without a transaction or for an action of another service, the inner interceptor marks the frame. The outer one then rethrows without recording a `FAILED` that would hide the cause. A transaction that fails to commit after the use case returned is not marked, and is recorded as `FAILED`.
- **The manifest lists both lists.** The audit manifest from ADR 0006 shows `deniedOn` and `failedOn` for each action, so a reviewer sees in the pull request which refusals and errors an action records.
- **What this replaces in ADR 0006**: the bullet *Outcome and reason from the exception*, the sentences of *Declared on the use case* that describe a single interceptor inside the transaction, and the consequence that the interceptor must run inside `@Transactional`.

## Considered options

- **A global setting that turns off recording failures.** Rejected: it also drops the refusals the trail exists for, such as a caller changing another user's data, and nobody notices until the trail is needed.
- **The exception decides whether it is recorded**, with a default `isAudited()` on `AuditReason`. It keeps the rule in one place per kind of failure, but exceptions the service does not own, such as `ForbiddenException`, cannot take part, and the manifest cannot show what an action records.
- **One list of recorded failures, with the outcome still taken from the exception.** It cannot record a framework refusal as `DENIED`.
- **No refusals by default**, so that nothing is recorded unless listed. Rejected for the reason ADR 0006 rejects allowlisted values: every forgotten list silently drops the refusals the trail is for.
- **Every failure by default** (`failedOn = {Exception.class}`), so that leaving out the lists misclassifies at most. Rejected: every database outage and bug would land in the immutable log as an audit event, duplicating logs and traces and burying the refusals among them.
- **One interceptor outside security checks.** It sees every refusal, but `SUCCESS` would be written outside the action's transaction, and could survive a change that rolled back.
- **One interceptor inside the transaction, with refusals checked by the use case itself.** Rejected: refusals by `@RolesAllowed` and `@PermissionsAllowed` would be lost without any error, and they are the most common kind.

## Consequences

- Errors are left to logs and traces unless an action lists them. Recording one is a decision per action, visible in the manifest and in review.
- A refusal thrown by a service's own exception that neither extends `ForbiddenException` nor is listed in `deniedOn` is not recorded. Authorization exceptions should therefore extend `ForbiddenException`.
- A refusal by `@RolesAllowed` happens before the use case runs, so it has no target. Its record is keyed by the actor, or by its event ID for an anonymous caller.
- The order depends on Quarkus' priorities: 150 for security checks and 200 for `@Transactional`. Tests in `libs/audit` cover both a refusal by `@RolesAllowed` and a success that rolls back with its transaction, so an upgrade that moves either priority fails the build instead of the audit trail.
- A failing action no longer holds a second database connection while its failure is recorded, because its own transaction has already ended.
