---
status: accepted
date: 2026-11-21
---

# Client access through the BFF and the public API

Clients reach the services through two entry points: the BFF for browser-based clients and the public API for everything else. The services themselves are never reachable from outside. The product needs a public token API for integrations, custom apps and API tokens anyway, and browsers cannot keep tokens safe. The lab mirrors the method intended for the product.

## Decision

- **Web and desktop use the BFF.** The BFF is a Quarkus service using `quarkus-oidc` with `application-type=web-app`, acting as a confidential OIDC client of Identity. It serves the web app's own site, so its cookie is first-party: `SameSite=Strict`, `HttpOnly`, plus CSRF protection on state-changing requests. Sessions are stored server-side through a database or Redis token state manager, not in the default encrypted cookie, so logout and revocation take effect immediately. The BFF has its own OpenAPI document and calls services through the per-service Java clients, propagating the user's access token.
- **Every other client uses the public API.** That covers Penvire's own native apps, custom apps, partner apps and scripts. A gateway on one public host verifies the token and its sender binding, rate-limits per client and tenant, and routes each path unchanged to the owning service. OAuth clients use authorization code with PKCE (or client credentials for machine clients). Their tokens are bound to a client key with DPoP. Confidential clients authenticate with `private_key_jwt` and never with shared secrets.
- **Only registered clients receive tokens.** Penvire registers its own apps, including a public CLI client with localhost redirects, and partner apps; users consent to partner apps per tenant. A tenant admin registers a custom app with its redirect URIs, client type and allowed scopes. The app is restricted to that tenant, and the admin's approval stands in for per-user consent. There is no dynamic client registration and no client ID metadata documents.
- **API tokens** serve scripts and CI. They are opaque, carry a recognisable prefix, are shown once, are stored hashed, and are scoped, tenant-bound, expiring and revocable. The gateway exchanges an API token for a short-lived JWT, so services see only JWTs. API tokens are plain bearer secrets without sender binding. Mandatory expiry, narrow scopes and leak scanning compensate.
- **Services trust nothing at the edge.** They accept calls only on the internal network over mTLS and check the token's audience, the operation's scopes, the caller's tenant membership and role, and, for `service` operations, that the calling workload is one of the operation's declared callers, on every request, whichever entry point the request came through. A service that calls another on a user's behalf first exchanges the user's token for one addressed to the target service (RFC 8693).

## Considered options

- **All first-party clients through the BFF, native apps included.** One auth model and one spec. Rejected because the public token API is needed anyway, and routing native apps through a cookie BFF would turn it into a second gateway.
- **Services exposed directly, without a gateway.** Rejected: every service would become internet-facing and would have to rate-limit and handle sender binding itself.
- **Apps work without registration** (client ID metadata documents or dynamic client registration, plus user consent). Rejected: only registered apps should count, so tenant admins keep control and every action can be attributed to a known client.
- **API tokens as long-lived JWTs.** Rejected: they cannot be revoked before expiry, and services would need a second validation path.

## Consequences

- Identity must support authorization code with PKCE, client credentials, `private_key_jwt`, DPoP, token exchange, consent, and client and API token management. These arrive in phases: first-party native apps first, then custom apps and API tokens, then partner apps.
- The contracts publish a merged `public-api` document for these clients ([ADR 0001](0001-versioned-contracts-through-published-packages.md)). Every operation in it declares the scopes it needs.
- The clients repository README ("all logging in through the lab BFF") holds only for web and desktop from now on.
