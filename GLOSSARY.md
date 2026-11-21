# Penvire Lab

The lab rebuilds the security-critical parts of the Penvire platform by hand. These terms are shared by the backend, contracts and clients.

## Clients

**First-party app**:
An app built and registered by Penvire, such as the web, desktop, iOS and Android apps or the CLI.
_Avoid_: official app, internal app

**Custom app**:
An app built by a tenant for its own use and registered by that tenant's admin; it only ever sees that tenant.
_Avoid_: private app, tenant app, integration

**Partner app**:
An app built by a third party, registered by Penvire and allowed into a tenant only after a user consents.
_Avoid_: third-party app, external app

**API token**:
A long-lived secret a user creates to let a script act with a chosen subset of their scopes in one tenant.
_Avoid_: API key, personal access token

**Scope**:
A named permission an operation requires and a client must hold, for example `engagement:read`.
_Avoid_: permission, right
