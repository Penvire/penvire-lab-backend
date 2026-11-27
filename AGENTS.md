# Penvire Lab backend

All backend services of Penvire Lab: Identity (the lab's own OpenID provider), Tenant, Engagement, Evidence, Audit and the BFF. This repository also holds the lab documentation: charter, architecture and ADRs.

> Penvire Lab is a learning project. It never runs in production, never holds real data and shares no code with the product. Ideas move to the product only by reimplementation or a reviewed copy.

## Repository map

```text
build-logic/            Gradle convention plugins shared by all services
services/<service>/     one Quarkus service each, hexagonal:
  src/main/java/.../<service>/
    domain/             entities and rules, no framework code
    application/        use cases and ports
    adapter/in/         REST
    adapter/out/        database, Kafka, other services
libs/                   shared libraries for cross-cutting infrastructure and shared abstractions
infrastructure/local/   Traefik, TLS and the observability stack
docs/                   charter, architecture, adr/, tracks/, agents/
GLOSSARY.md             domain vocabulary
```

## Development environment

Enter the environment with `devenv shell`, or run `direnv allow` once. It provides Java 25 and Gradle 9 (running on JDK 25). Always run tools through it. Quarkus Dev Services need Docker.

Git hooks run automatically: a commit-msg hook rejects messages that are not Conventional Commits (the same rule as CI), actionlint checks workflow files, and a pre-commit hook runs `spotlessApply` when Java files are staged; if it reformats anything, the commit fails, so stage the formatted files and commit again.

### Local infrastructure

`devenv up` (or `devenv up -d`, stop with `devenv processes down`) starts the shared infrastructure. Services run separately with `quarkusDev`.

| What | Where |
| --- | --- |
| Traefik gateway (local CA, see `infrastructure/local/`) | `https://localhost:8443`, `$PENVIRE_HTTPS_PORT` if 8443 is taken |
| OpenFGA (API / gRPC / Playground) | `http://localhost:8090` / `localhost:8091` / `http://localhost:3001/playground` |
| OpenTelemetry Collector (OTLP) | `localhost:4317` (gRPC), `localhost:4318` (HTTP) |
| Grafana (traces, logs, metrics; no login) | `http://localhost:3000` |
| Tempo / Loki / Prometheus | `:3200` / `:3100` / `:9090` |

Services send traces, metrics and logs to the collector without any configuration. Databases are not part of devenv: Quarkus Dev Services start one PostgreSQL container per service in dev mode and in tests.

### Building and running

- `./gradlew build`: compile, Error Prone with NullAway, tests, JaCoCo and Spotless. Use the wrapper, never a global `gradle`.
- `./gradlew spotlessApply`: format with google-java-format before committing.
- `./gradlew :services:identity:quarkusDev`: one service in dev mode with live reload. API on port 8081; health on the management port at `http://localhost:9000/q/health`, never routed publicly.

Convention plugins in `build-logic/`:

- `penvire.java-library`: every module. Error Prone and NullAway (JSpecify, `com.penvire`), Spotless, JaCoCo, Lombok.
- `penvire.quarkus-library`: shared libraries in `libs/`. Quarkus BOM; `quarkusDev` disabled.
- `penvire.quarkus-service`: services. REST, health, OpenTelemetry, MapStruct; shared config defaults in a generated `META-INF/microprofile-config.properties`; `audit-manifest.yaml`, written by `./gradlew build` from the `@Audited` methods and committed (CI fails if it is out of date, and comments on each pull request with what it changes).

New services: add `include(":services:<name>")` to `settings.gradle.kts` and a `build.gradle.kts` that applies `penvire.quarkus-service`, give it its own HTTP and management ports in `application.properties`, register `services/<name>` in `release-please-config.json` and `.release-please-manifest.json`, and add `services/<name>/version.txt` containing `0.0.0` (release-please only updates that file if it exists).

## Rules

- Never push to `main`. Create a branch (`feat/`, `fix/` or `chore/`), push it and open a pull request. The maintainer merges.
- Pull requests are squash-merged. The PR title becomes the commit on `main`, so it must be a Conventional Commit; mark breaking changes with `!`.
- Commits are GPG-signed; `main` rejects unsigned commits.
- PR body: a one-paragraph summary, then a `## Test plan` section.
- Releases are managed by release-please. Do not bump versions or edit `CHANGELOG.md` by hand.
- Everything starts at 0.x. Below 1.0.0, `feat` and `fix` bump the patch and a breaking change (`!`) bumps the minor, so a new minor always means breaking. Leaving 0.x is deliberate: a commit with a `Release-As: 1.0.0` footer (ADR 0001 in penvire-lab-backend).
- The PR title becomes the changelog entry. Keep one user-facing change per PR. If a PR contains several, list every `feat` and `fix` on its own line in the PR description, between `BEGIN_COMMIT_OVERRIDE` and `END_COMMIT_OVERRIDE`; release-please uses that block instead of the title.

## Agent docs

- [Issue tracker](docs/agents/issue-tracker.md): GitHub Issues for `Penvire/penvire-lab-backend`
- [Triage labels](docs/agents/triage-labels.md)
- [Domain docs](docs/agents/domain.md): where the glossary and ADRs live

## Related repositories

- `../contracts`: [penvire-lab-contracts](https://github.com/Penvire/penvire-lab-contracts)
- `../clients`: [penvire-lab-clients](https://github.com/Penvire/penvire-lab-clients)
