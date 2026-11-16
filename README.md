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

## Related

- [penvire-lab-contracts](https://github.com/Penvire/penvire-lab-contracts): API and event contracts
- [penvire-lab-client](https://github.com/Penvire/penvire-lab-client): web UI

## License

[AGPL-3.0](LICENSE)

## Contributing

Commits must be signed and follow [Conventional Commits](https://www.conventionalcommits.org/). Pull requests are merged with a merge commit.

## Security

See the [Penvire security policy](https://github.com/Penvire/.github/blob/main/SECURITY.md).

Lab findings may be reported as public issues. Attacking the lab is part of the project.
