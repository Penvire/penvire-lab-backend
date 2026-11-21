---
status: accepted
date: 2026-11-21
---

# Versioned contracts through published packages

All API contracts live in [penvire-lab-contracts](https://github.com/Penvire/penvire-lab-contracts). Services and clients consume them only as versioned packages published to the maintainer's Forgejo package registry: Maven group `dev.penvire.lab` and npm scope `@penvire-lab`. Nothing reads contract files from another repository directly. Each service owns one OpenAPI document. release-please gives each document an independent version, so a change in one service never forces a release of the others.

## Decision

- One release component per service document (`openapi/<service>/openapi.yaml`). release-please writes `info.version` in the release PR. Nobody bumps versions by hand.
- A breaking change is marked with `!` in the pull request title. CI compares each document with the pull request's base (`openapi-changes` plus checks for the Penvire extensions) and fails when it finds a breaking change and the title has no `!`.
- Every operation declares `x-penvire-audience`: `api` (reachable from outside), `service` (internal service-to-service) or `protocol` (OpenID and similar protocol endpoints). Every `api` operation declares `x-penvire-scopes` from a shared scope catalogue.
- Every `service` operation declares `x-penvire-callers`, the workloads allowed to call it, from a shared workload catalogue. Services enforce that list. `api` operations declare no callers: they are reached only through the BFF and the public API gateway ([ADR 0002](0002-client-access-through-bff-and-public-api.md)), and a service that needs the same capability gets a `service` operation instead. The receiving service is never declared, because it is the owner of the document.
- `api` and `service` paths carry a path major (`/v1/…`, `/internal/v1/…`), versioned as described below.

Published artifacts:

| Artifact | Built from | Used by |
| --- | --- | --- |
| `<service>-openapi` (bundled YAML) | the service document | the service, which generates its server interfaces from it |
| `<service>-client` (Java) | the service document | other services and the BFF (server-to-server only) |
| `bff-openapi`, `@penvire-lab/bff-client` (TypeScript) | the BFF's own document | web and desktop clients |
| `public-api` (bundled YAML), `@penvire-lab/api-client` (TypeScript) | all `api` operations of all services, merged | everything that uses the public API ([ADR 0002](0002-client-access-through-bff-and-public-api.md)) |

## Versioning

There are three version numbers, each with its own job:

- **Contract version**: the semantic version of a service document (`info.version`), written by release-please. It is the version of `<service>-openapi` and `<service>-client`, and tells package consumers whether an upgrade breaks their code. Any breaking change to the document is a major bump.
- **Path major**: the `/v<major>/` in `api` and `service` paths, kept separately for each service. Wire clients (apps, partners, scripts) code against it. It changes only when an existing operation has to change incompatibly. The new major is added next to the old one, which is a minor contract bump. The old operations are marked `deprecated` and announce their end with `Deprecation` and `Sunset` headers. They are removed after the sunset date, which is a major contract bump. Contract major and path major therefore drift apart: identity can be at contract 3.0.0 and serve `/v2`.
- **Service version**: the release number of the implementation in this repository. A service pins the contract version it implements; the two numbers are otherwise unrelated.

Services are versioned independently. Identity serving `/v2` while tenant serves `/v1` is normal, because each path belongs to exactly one service and the gateway routes by path.

- A deployed service serves every path major its current contract contains.
- Package consumers upgrade by semantic version: patch and minor releases never break them. Wire clients must move to the new path major before its predecessor's sunset date. An old path major is removed only when no service still pins a client that calls it.
- Services retire old path majors on shared sunset dates, so `public-api` takes one major bump per batch rather than one per service.

### Before 1.0.0

- Every contract starts at 0.x, in development. Its paths stay at `/v1` and change in place, with no parallel path majors and no deprecation window. CI rejects other path majors while the contract is below 1.0.0.
- A breaking change still needs `!`. release-please then bumps the minor (0.3.2 → 0.4.0), while `feat` and `fix` bump the patch (`bump-patch-for-minor-pre-major`). Within 0.x, a new minor therefore always means breaking, which matches how npm caret ranges and Cargo already read 0.x versions.
- Leaving 0.x is a deliberate decision for each service, made with a `Release-As: 1.0.0` commit footer.
- Services in this repository and the apps in penvire-lab-clients use the same pre-1.0 release-please settings.

### `public-api`

`public-api` is merged from every service, so no single directory's commits describe its changes. When a service releases, CI rebuilds `public-api` and compares it with its last release using `openapi-changes`:

| Difference | Release |
| --- | --- |
| Breaking (an operation or field removed, such as a sunset path major) | major |
| Added operations or fields (such as a new path major) | minor |
| Anything else, such as documentation | patch |
| No difference | none |

Its version belongs to the package (the bundled YAML and `@penvire-lab/api-client`) and never appears on the wire. It can be at 3.0.0 while every service serves `/v2`.

## Considered options

- **Hand-maintained versions plus a bundle version and a custom release ledger.** The predecessor lab worked this way. Rejected: it duplicates what release-please already does, and the two release ledgers would disagree.
- **Consumers generate their own clients from the YAML.** Rejected for Java and TypeScript, because every consumer would pin its own generator version and drift. Still possible for native apps and third parties, which receive the bundled `public-api` YAML.
- **A public registry (Maven Central, npmjs) or GitHub Packages.** Rejected: the lab keeps its packages on infrastructure the maintainer controls, readable without authentication.
- **Path major equal to contract major.** Rejected: removing a sunset path major breaks package consumers and needs a contract major bump, even though the remaining paths are unchanged. Keeping the numbers equal would force a pointless new path major.
- **A release-please component for `public-api`.** Rejected: its changes come from other directories, so its commits cannot describe them. The diff can.
- **Breaking-change check against the last release tag.** Rejected: once a breaking change is merged but not yet released, every later pull request would see it and fail without `!`. Comparing with the pull request's base checks exactly that pull request's changes, and works before the first release.
