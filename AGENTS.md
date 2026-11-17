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
infrastructure/local/   local Traefik, TLS and compose files
docs/                   charter, architecture, adr/, tracks/, agents/
GLOSSARY.md             domain vocabulary
```

## Development environment

Enter the environment with `devenv shell`, or run `direnv allow` once. It provides Java 25 and Gradle 9 (running on JDK 25). Always run tools through it.

Git hooks run automatically: a commit-msg hook rejects messages that are not Conventional Commits (the same rule as CI), and actionlint checks workflow files.

Build and test everything with `./gradlew build`. Run one service in dev mode with `./gradlew :services:identity:quarkusDev` (API on port 8081; health on the management port at `http://localhost:9000/q/health`, never routed publicly). Use the wrapper, never a global `gradle`. New services: add `include(":services:<name>")` to `settings.gradle.kts` and a `build.gradle.kts` that applies `penvire.quarkus-service`, register `services/<name>` in `release-please-config.json` and `.release-please-manifest.json`, and add `services/<name>/version.txt` containing `0.0.0` (release-please only updates that file if it exists).

## Rules

- Never push to `main`. Create a branch (`feat/`, `fix/` or `chore/`), push it and open a pull request. The maintainer merges.
- Pull requests are squash-merged. The PR title becomes the commit on `main`, so it must be a Conventional Commit; mark breaking changes with `!`.
- Commits are GPG-signed; `main` rejects unsigned commits.
- PR body: a one-paragraph summary, then a `## Test plan` section.
- Releases are managed by release-please. Do not bump versions or edit `CHANGELOG.md` by hand.
- The PR title becomes the changelog entry. Keep one user-facing change per PR. If a PR contains several, list every `feat` and `fix` on its own line in the PR description, between `BEGIN_COMMIT_OVERRIDE` and `END_COMMIT_OVERRIDE`; release-please uses that block instead of the title.

## Agent docs

- [Issue tracker](docs/agents/issue-tracker.md): GitHub Issues for `Penvire/penvire-lab-backend`
- [Triage labels](docs/agents/triage-labels.md)
- [Domain docs](docs/agents/domain.md): where the glossary and ADRs live

## Related repositories

- `../contracts`: [penvire-lab-contracts](https://github.com/Penvire/penvire-lab-contracts)
- `../clients`: [penvire-lab-clients](https://github.com/Penvire/penvire-lab-clients)
