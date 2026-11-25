---
status: accepted
date: 2026-11-24
---

# Opaque refresh tokens, and revoked signing keys kept until they expire

[#14](https://github.com/Penvire/penvire-lab-backend/issues/14) made refresh tokens ML-DSA-65 JWTs, and [ADR 0007](0007-signing-keys-encrypted-in-identity-database.md) therefore gave Identity a second kind of signing key: refresh keys, never published and kept for six months. A signature on a refresh token buys nothing. Only Identity ever reads one, and the OAuth 2.0 Security BCP ([RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)) requires refresh tokens of public clients to be rotated on every use with reuse detection, which needs a stored row per token family anyway; so do sign-out and revoking a user's sessions. Once that row exists, Identity can look the token up instead of verifying it. So refresh tokens become opaque, every signing key is published, and the key purposes disappear. Designing the signing-key entity also showed that a compromised key has no place in ADR 0007's model except deletion, which leaves no trace of it in the table. So a revoked key now stays, marked revoked, until it would have expired. The rest of ADR 0007 stays in force.

## Decision

- **Refresh tokens are opaque.** A refresh token is 32 random bytes from `SecureRandom`, base64url-encoded. Identity stores only its SHA-256 hash; the token's entropy makes a slow password hash unnecessary. The row also holds the token family, the idle timeout and the absolute lifetime. Every use issues a new refresh token and marks the old one used; presenting a used one revokes the whole family. The details belong to the refresh-token slice.
- **Every signing key is published.** ADR 0007's `purpose`, its refresh keys and their six-month retention are gone. A key signs access and ID tokens and stays published for the longest lifetime of those tokens plus 30 seconds of skew after it stops signing. At any instant, exactly one key per algorithm signs.
- **A revoked key keeps its row.** Revoking sets `revoked_at` to the current instant and deletes the private key; the four instants stay as they were. `revoked_at` belongs to the key's lifetime and lies before `publish_until`. A key can be revoked before it is published, so a key that leaks early still leaves a row. From then on the key is neither published nor used to sign, and tokens it signed stop verifying. The row is deleted at `publish_until`, like any other. `revoked_at` is part of the row MAC.
- **Revoking also rotates the KEK.** A copy of the row from before the revocation, restored from a backup, still carries a valid MAC. Rotating the KEK, which re-seals every private key and re-computes every MAC under the new key, makes such a copy fail verification, but only once the old KEK is removed from configuration: while it is still listed, a row naming it still verifies.
- **The phase of a key is derived, never stored.** Whether a key is pending, signing, retired, expired or revoked follows from its four instants, `revoked_at` and the current instant. A stored status could contradict them.

## Considered options

- **Keep signed refresh tokens**, with the key purposes renamed by what they sign. Rejected: rotation, reuse detection and sign-out still need the stored row, so the signature only adds keys to manage.
- **Delete a revoked key's row**, as ADR 0007 decided. It is simpler, but an unknown and a revoked `kid` look the same, and the table no longer shows that the key ever existed. The audit event records the revocation either way.
- **Cut the lifetime short instead**: set `sign_until` and `publish_until` to the revocation instant. It needs no extra column and has the same effect on tokens. Rejected: a revoked key would look like one that expired normally, and cleanup would delete its row at once.
- **A stored status column.** Rejected: it duplicates the instants and can contradict them.

## Consequences

- #14's statement that refresh tokens use ML-DSA-65 no longer applies.
- The `signing_key` table has no `purpose` column, and it gains a nullable `revoked_at`.
- The JWKS holds about two to three keys per algorithm, and no key is kept longer than its tokens live.
- Identity needs a refresh-token table before it can issue refresh tokens.
