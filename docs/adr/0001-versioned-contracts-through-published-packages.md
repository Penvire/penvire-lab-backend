---
status: accepted
date: 2026-11-21
---

# Versioned contracts through published packages

All API contracts live in [penvire-lab-contracts](https://github.com/Penvire/penvire-lab-contracts). Services and clients consume them only as versioned packages published to the maintainer's Forgejo package registry: Maven group `dev.penvire.lab` and npm scope `@penvire-lab`. Nothing reads contract files from another repository directly. Each service owns one OpenAPI document. release-please gives each document an independent version, so a change in one service never forces a release of the others.

## Decision

- One release component per service document (`openapi/<service>/openapi.yaml`). release-please writes `info.version` in the release PR. Nobody bumps versions by hand.
- A breaking change is marked with `!` in the pull request title. CI compares each document with its last release tag (`openapi-changes`) and fails when it finds a breaking change and the title has no `!`.
- Every operation declares `x-penvire-audience`: `api` (reachable from outside), `service` (internal service-to-service) or `protocol` (OpenID and similar protocol endpoints). Every `api` operation declares `x-penvire-scopes` from a shared scope catalogue.
- `api` and `service` paths carry the contract's major version (`/v1/…`, `/internal/v1/…`). A breaking change to a published `api` operation adds a new path major. The previous one stays available for a deprecation window and announces its end with `Deprecation` and `Sunset` headers.

Published artifacts:

| Artifact | Built from | Used by |
| --- | --- | --- |
| `<service>-openapi` (bundled YAML) | the service document | the service, which generates its server interfaces from it |
| `<service>-client` (Java) | the service document | other services and the BFF (server-to-server only) |
| `bff-openapi`, `@penvire-lab/bff-client` (TypeScript) | the BFF's own document | web and desktop clients |
| `public-api` (bundled YAML), `@penvire-lab/api-client` (TypeScript) | all `api` operations of all services, merged | everything that uses the public API ([ADR 0002](0002-client-access-through-bff-and-public-api.md)) |

`public-api` has its own release component and version, because any service's `api` operations can change it.

## Considered options

- **Hand-maintained versions plus a bundle version and a custom release ledger.** The predecessor lab worked this way. Rejected: it duplicates what release-please already does, and the two release ledgers would disagree.
- **Consumers generate their own clients from the YAML.** Rejected for Java and TypeScript, because every consumer would pin its own generator version and drift. Still possible for native apps and third parties, which receive the bundled `public-api` YAML.
- **A public registry (Maven Central, npmjs) or GitHub Packages.** Rejected: the lab keeps its packages on infrastructure the maintainer controls, readable without authentication.
