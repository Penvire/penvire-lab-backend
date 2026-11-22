# Penvire Lab — JWT and security handout

## Start here tomorrow

Start with the framework-independent JWT core, not the full security integration.

1. Read this handout and the repository's `AGENTS.md`.
2. Check the current branch and working tree. At handoff, the checkout is clean on `feat/jwt-lib`, commit `5b1ebbf`.
3. Inspect existing build conventions before adding `libs:jwt`.
4. Agree the public interfaces and the unresolved token-profile details below.
5. Write one meaningful failing verification test, implement it, and continue in small slices. Both RS256 and ML-DSA-65 belong in the first slice.

The user intends to write implementations. The assistant can help define interfaces, write tests, review, and verify. Do not silently take over implementation or commit/push without a request.

### Existing work

- Observability: [PR #12](https://github.com/Penvire/penvire-lab-backend/pull/12), merged.
- Problem details: [PR #13](https://github.com/Penvire/penvire-lab-backend/pull/13), merged.
- Reference those PRs for existing code and tests rather than copying their implementations into this handout.
- The coverage check before this design discussion passed all 21 tests, with 100% of the then-existing 18 executable Java lines covered. This describes a very small baseline, not coverage of the proposed JWT work.

## Goal and boundaries

This is a learning project. Implement JWT/JWS processing and the OIDC provider yourselves. Use Java cryptographic primitives; do not implement RSA or ML-DSA arithmetic.

The lab provider may later be replaced by Keycloak. Keep tenant authorization outside the provider. Standard protocols and explicit compatibility tests are the replacement boundary; do not assume identical token headers, claims, or delegation behavior across providers.

| Component | Responsibility |
| --- | --- |
| `libs:jwt` | Compact JWS processing, signing, signature verification, key serialization, and explicit token-profile validation. No network, database, Redis, or Quarkus authentication dependency. |
| Identity | OIDC/OAuth flows, token issuance, sessions, refresh rotation, introspection, token exchange, and signing-key lifecycle. |
| `libs:security` later | Bearer authentication, consumer JWKS resolution, cached introspection, and Quarkus `SecurityIdentity` integration. |
| Tenant | Application principal UUIDs, external identity mappings, user memberships, and explicit tenant grants for service principals. |
| Each API | Requested tenant context and authorization for its resources. |

Reuse the repository's library conventions. Creating the JWT library now is intentional: the user explicitly selected this learning scope rather than waiting for accidental duplication.

## Agreed decisions

### Algorithms and compatibility

- Support **ML-DSA-65 and RS256 in the first slice**.
- Prefer ML-DSA-65. Use RS256 only where trusted client/audience configuration explicitly requires or permits it.
- Generate RSA keys at **2048 bits**.
- ID-token signing follows the registered OAuth client's capabilities.
- Access-token signing follows the target API audience's capabilities. A native client normally transports the access token rather than verifying it.
- Refresh tokens use ML-DSA-65. Clients treat them as opaque values.
- There is no verification-time fallback. Do not retry a failed ML-DSA signature as RSA.
- Match the token algorithm against the trusted issuer/profile, key type, key algorithm, and intended token use. Never let the untrusted `alg` header alone select arbitrary cryptographic behavior.
- Accepting RSA as well as ML-DSA is a compatibility policy, not a requirement for post-quantum signatures.
- Proposed lab token-type headers were accepted without changes: access `at+jwt`, ID `JWT`, refresh `refresh+jwt`. These are distinct validation profiles, not universal assumptions about Keycloak.

### Java and JOSE support

- Java 25 provides ML-DSA through JCA: `KeyPairGenerator`, `Signature`, and `KeyFactory`. Support arrived in Java 24 through JEP 497.
- Use `ML-DSA-65` for the selected parameter set.
- ML-DSA JOSE/JWK support is standardized in **RFC 9964**, published May 2026. This is not merely a draft/private algorithm name.
- ML-DSA public JWKs use `kty=AKP`, `alg=ML-DSA-65`, and `pub` containing the base64url-encoded **raw public key**.
- Do not put Java's X.509/SPKI encoded key blob directly into `pub`.
- Java PKCS8 private-key persistence and RFC 9964 private-JWK encoding are different formats. Private-JWK export is not required for the first slice. Never publish private material in public JWKS.
- ML-DSA-65 signatures are 3,309 raw bytes, versus 256 bytes for RSA-2048. Base64url increases transmitted size. Test gateway/header limits during integration.
- ML-KEM is for key establishment, not JWT signing. Post-quantum JWT signatures do not make TLS/mTLS post-quantum.

### Quarkus compatibility finding

The exact Quarkus **3.40.1** source was checked:

- `OidcProvider` has no ML-DSA in its supported asymmetric algorithm allowlist.
- `JsonWebKeySet` accepts RSA, EC, and OKP key types, not AKP.
- JDK support therefore does not make stock `quarkus-oidc` locally verify ML-DSA JWTs.
- The planned custom JWT verifier and Quarkus authentication adapter fit the lab requirement.
- For a BFF using stock `quarkus-oidc`, use RS256 ID tokens initially. Custom backend consumers can still receive ML-DSA access tokens.
- Remote introspection can delegate access-token validation to Identity, but that is not local post-quantum signature verification.
- Native OIDC library compatibility remains to be investigated; do not claim it works yet.

### Clients, audiences, and delegation

- Initial interactive flow: **Authorization Code + PKCE**.
- Browser: the BFF holds OAuth tokens; the browser holds a session cookie.
- Native mobile: a separate public OAuth client uses the system browser and PKCE. Store refresh tokens in platform secure storage. Do not embed a client secret or service mTLS credential.
- Autonomous services use **Client Credentials with mTLS client authentication**.
- Services use mTLS at introspection and token-exchange endpoints.
- Each access token targets **one API audience**.
- User-delegated calls use token exchange. Permit only explicitly configured caller-to-target edges and scopes. Preserve the user subject and identify the acting service; do not expand user authority.
- Machine authority is distinct from delegated user authority.

### Sessions and refresh tokens

- Access-token lifetime: **5 minutes**.
- ID-token lifetime: **5 minutes**.
- Remembered refresh-session idle timeout: **30 days**.
- Remembered refresh-session absolute lifetime: **6 calendar months from login**.
- Successful refresh resets idle expiry but cannot extend absolute expiry. Replacement refresh tokens cannot outlive either limit.
- Each native or BFF login has an independent refresh-token family.
- Refresh consumes the old token and issues its replacement atomically.
- Reusing a consumed refresh token revokes that family, including its associated access-token validity through introspection.
- The BFF serializes refresh operations. No reuse grace or retry recovery is included initially.
- A lost successful refresh response followed by retry can revoke the family and require login. This tradeoff was explicitly accepted.
- Refresh stays bound to the original client and approved scopes. It cannot expand authorization.
- JWT clock skew: **30 seconds**. This does not extend authorization-cache freshness.
- BFF cookie/session persistence and Identity SSO lifetime are separate settings that still need to be designed when implementing those components.

### Principals and tenant authorization

- External account identity is `(iss, sub)`, treated as opaque strings.
- Tenant maps external identities to stable application-owned principal UUIDs.
- On first verified login, create a principal with **no tenant grants**.
- Email is a contact/login attribute, never the permanent identity key. Do not automatically merge accounts because their emails match.
- Replacing the provider requires explicit trusted identity-link migration; UUID-shaped subjects do not automatically match across providers.
- Tenant owns both user memberships and explicit grants for autonomous service principals.
- Tenant context is specified **per request**, not one globally active tenant in a login session.
- Identity does **not** query Tenant to attest membership during token issuance. That earlier idea was explicitly superseded for provider portability.
- APIs separately check token validity, tenant authorization, and resource permissions.
- Tenant identifiers in paths/headers are requested context, not proof of authorization. Exact HTTP representation is not settled.

### Caches and outages

- Use separate, bounded **in-memory caches in each consumer**. These caches do not require Redis.
- Cache token-introspection results per access token and consumer context, using a token digest rather than the raw token as the cache key.
- Refresh token status through authenticated introspection on miss or expiry. Introspection does not issue replacement tokens.
- Session/token revocation must take effect within **5 seconds**.
- Cache Tenant authorization separately. Membership removal must also take effect within **5 seconds**.
- Each five-second freshness budget starts when its outbound check begins. Network time consumes the budget. Discard a result arriving after its deadline.
- Cache hits never reset freshness. Do not hide extra stale caches behind Identity or Tenant. Do not cache token validity beyond token expiry.
- If required fresh status cannot be obtained, block the protected request with **503**. An inactive token produces **401**. Permission denial is a separate authorization outcome.
- JWKS cache lifetime: **5 minutes**. Refresh on unknown `kid`; coalesce concurrent refreshes and rate-limit unknown-key refresh attempts.
- A failed JWKS refresh must not erase still-fresh known keys. If a required key cannot be obtained, return 503. If a successful refresh confirms the key remains unknown, reject the token.
- Expired JWKS data is not used as a stale fallback under the selected policy.

### Signing-key lifecycle

- Identity stores encrypted private-key material through its database adapter.
- The master encryption key lives outside that database.
- Use separate key rings for public access/ID tokens and internal refresh tokens.
- Rotate automatically every **7 days**, with manual rotation support.
- Publish the next public verification key at least **5 minutes before scheduled activation**.
- Retiring a signing key stops new signatures; it does not revoke issued tokens.
- Retain needed verification keys until the last associated token expires, including clock skew. Refresh keys require much longer retention than public-token keys.
- Compromise revocation is distinct from ordinary retirement and still needs an explicit operational design.

## First implementation slice

### Deliverable

A framework-independent library that signs and verifies compact JWS using both RS256 and ML-DSA-65, with correct public-key encoding and a defined access-token validation profile.

### Suggested order

1. Define the public verification boundary: token, explicit validation policy, trusted key resolution/input, clock, and verified result or typed failure. Do not cast external subject strings into domain UUIDs.
2. Agree parser limits and required access-token claims before writing their tests.
3. Verify an independently generated RS256 token using a supplied trusted public key.
4. Verify an independently generated ML-DSA-65 token through the same public boundary.
5. Add strict compact-JWS parsing and rejection cases. Verify the original encoded signing input; do not deserialize and reserialize before signature verification.
6. Implement signing for both algorithms. Cross-check output with another implementation; do not rely only on your own signer passing your own verifier.
7. Add RSA and AKP public JWK import/export and JWKS round trips. Test against external vectors as well as local round trips.
8. Complete profile validation and deterministic time-boundary tests.
9. Run module checks, inspect coverage, and stop at this boundary.

Keep the API small. The shared boundary should not require `RSAPublicKey`; use algorithm-specific implementations behind general Java key interfaces. Do not build an arbitrary provider/plugin framework.

### Out of scope for this slice

- Database schema, encrypted-key repository, and rotation scheduler.
- OIDC authorization/token endpoints and refresh-session persistence.
- Remote JWKS fetching, introspection clients, and authorization caches.
- Tenant membership lookup and principal provisioning.
- Quarkus HTTP authentication mechanisms and native app integration.
- Hybrid/composite signatures, JWE, ML-KEM, and post-quantum TLS.

These remain part of the wider design, not acceptance criteria for the first JWT-core change.

## Test checklist

Use public seams. Confirm interface shape before writing tests. Prefer a failing test followed by the user's implementation, then the next test.

### Signatures and interoperability

- [ ] Independently generated RS256 and ML-DSA-65 signatures verify.
- [ ] Each emitted signature verifies with an independent implementation.
- [ ] Modified header, payload, or signature is rejected.
- [ ] Wrong key, wrong key type, wrong ML-DSA parameter set, and truncated signatures are rejected.
- [ ] Unsupported algorithms, `none`, and policy/key-algorithm mismatches are rejected.
- [ ] Failed verification never falls back to a different algorithm.

### Parsing and trust boundaries

- [ ] Malformed compact serialization, invalid base64url, invalid JSON, duplicate members, and excessive input sizes are rejected under the agreed profile.
- [ ] Unsupported critical headers and unsupported payload-encoding modes are rejected.
- [ ] Header-controlled URLs or embedded keys cannot replace configured key trust.
- [ ] Failures do not return tokens, private keys, or sensitive internal details to callers.

### Profile validation

- [ ] Wrong issuer, missing/wrong audience, and wrong token type are rejected.
- [ ] Expired, future-not-before, and future-issued tokens follow explicit clock-skew rules.
- [ ] Missing or incorrectly typed required claims are rejected.
- [ ] Access-token verification rejects ID and refresh tokens.
- [ ] Time tests use an injected clock, not sleeps.

### Key encoding

- [ ] RSA public JWKs follow the RSA representation.
- [ ] ML-DSA public JWKs use AKP and raw public-key bytes under RFC 9964.
- [ ] Invalid lengths, algorithm mismatches, and malformed key material are rejected.
- [ ] Public exports never contain private-key material.

### Later integration tests, not first-slice tests

Test rotation overlap, concurrent refresh reuse, introspection freshness including network delay, Tenant membership removal, bounded cache behavior, outages, delegated actor/subject handling, mTLS caller authorization, and provider/client interoperability when those boundaries exist.

## Implementation details still to settle

The architecture is agreed, but do not silently invent these contracts:

- Exact core Java interfaces and failure taxonomy.
- Required claims per token profile, including `client_id`, `jti`, session/family identifiers, optional `nbf`, and timestamp representation.
- Token, JSON nesting, claim-count, audience-count, and key-size limits.
- How missing versus ambiguous `kid` values are treated.
- Private-key encryption format, master-key provisioning/rotation, and recovery after loss of that key.
- JWKS refresh throttling and network timeout values.
- Delegated actor representation and correspondence with the eventual Keycloak token-exchange configuration.
- Who performs first-login principal resolution and how simultaneous first logins avoid duplicate principals.
- The native platform/OIDC library and its ML-DSA ID-token support.
- Login credentials, authorization-code lifetime, BFF session/SSO policies, and detailed OAuth endpoint contracts. These are later Identity work, not reasons to block the JWT core.

Avoid reopening settled choices unless implementation evidence exposes a contradiction. Label any new default as a proposal.

## References and next-session guidance

### Primary references

- [JEP 497: Java ML-DSA](https://openjdk.org/jeps/497)
- [RFC 9964: ML-DSA for JOSE/COSE](https://www.rfc-editor.org/rfc/rfc9964.html)
- [IANA JOSE algorithms and key types](https://www.iana.org/assignments/jose/jose.xhtml)
- [RFC 7515: JWS](https://www.rfc-editor.org/rfc/rfc7515.html)
- [RFC 7519: JWT](https://www.rfc-editor.org/rfc/rfc7519.html)
- [RFC 8725: JWT best practices](https://www.rfc-editor.org/rfc/rfc8725.html)
- [RFC 9068: JWT access-token profile](https://www.rfc-editor.org/rfc/rfc9068.html)
- [RFC 7662: introspection and caching](https://www.rfc-editor.org/rfc/rfc7662.html)
- [RFC 8693: token exchange](https://www.rfc-editor.org/rfc/rfc8693.html)
- [Quarkus 3.40.1 OIDC algorithm allowlist](https://github.com/quarkusio/quarkus/blob/3.40.1/extensions/oidc/runtime/src/main/java/io/quarkus/oidc/runtime/OidcProvider.java)
- [Quarkus 3.40.1 JWKS key-type handling](https://github.com/quarkusio/quarkus/blob/3.40.1/extensions/oidc/runtime/src/main/java/io/quarkus/oidc/runtime/JsonWebKeySet.java)

Old security code is reference material, not the new specification:

<https://github.com/QF0xB/foxshield-lab-test1/tree/main/platforms/security/src/main/java/dev/foxshield/lab/platform/security>

It mixed JWT processing, key loading, HTTP authentication, and Redis revocation. Preserve the agreed new boundaries instead of copying that structure.

### Commands and repository rules

Run tools through the development environment and use the Gradle wrapper:

```bash
devenv shell -- ./gradlew :libs:jwt:test
devenv shell -- ./gradlew :libs:jwt:spotlessApply :libs:jwt:build
devenv shell -- aislop scan --changes -d
```

These commands assume the new module has been registered. Before creating it, inspect existing conventions and test dependencies. Repository rules require signed Conventional Commits and feature branches; commit and push only when requested.

### Suggested skills for the next agent

- `tdd`: agree public seams, then write meaningful tests against those seams.
- `codebase-design`: keep JWT processing independent from infrastructure and framework adapters.
- `verify-and-stop`: run the required proof and stop without expanding the slice.
- `git-workflow-and-versioning` and `git-signing`: when editing or explicitly asked to commit.
- `grilling`: only for unresolved decisions, not to restart the whole interview.

### Suggested opening request

> Read `docs/jwt-security-handout.md` and repository instructions. Help me define the first JWT-core interfaces and agree the required access-token claims and parser limits. Then write one meaningful failing verification test. I will implement it. Both RS256 and ML-DSA-65 belong in this slice. Do not implement the library for me or commit anything yet.
