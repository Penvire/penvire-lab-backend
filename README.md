# Penvire Lab backend

> **Penvire Lab** is a learning project. It never runs in production and never holds real data. Lab and product share no code.

**Status:** not started yet.

The lab implements the security-critical parts of the Penvire platform by hand, to learn where proven components succeed and where they fail.

## What goes here

- All lab backend services: Identity (the lab's own OpenID provider), Tenant, Engagement, Evidence, Audit and the BFF
- Local development infrastructure
- Lab documentation: charter, architecture and architecture decision records

## Planned stack

Java 25, Quarkus and Gradle, with devenv as the development environment.

## Identity server contract

Identity builds against the released OpenAPI YAML from the Forgejo Maven registry. The
`penvire.openapi-server` convention plugin generates Quarkus REST interfaces and models
under `services/identity/build/generated/openapi/server`. Gradle compiles these sources
as part of the normal build. REST adapters implement the generated interfaces.

To generate the sources on their own, run `./gradlew :services:identity:generateOpenApiServer`.
The contract version is pinned by `identity-contract` in `gradle/libs.versions.toml`.

## Related

- [penvire-lab-contracts](https://github.com/Penvire/penvire-lab-contracts): API and event contracts
- [penvire-lab-clients](https://github.com/Penvire/penvire-lab-clients): web, desktop and mobile apps

## License

[AGPL-3.0](LICENSE)

## Contributing

Commits must be signed and follow [Conventional Commits](https://www.conventionalcommits.org/). Pull requests are merged with a merge commit.

## Security

See the [Penvire security policy](https://github.com/Penvire/.github/blob/main/SECURITY.md).

Lab findings may be reported as public issues. Attacking the lab is part of the project.
