---
status: accepted, partly superseded by ADR-0009
date: 2026-11-23
---

# Signing keys stored encrypted in Identity's database and rotated every 30 days

Identity signs tokens with ML-DSA-65 and, where a client or API requires it, RS256 ([#14](https://github.com/Penvire/penvire-lab-backend/issues/14)). Its keys must survive restarts, be shared by every Identity instance, and still verify every token they signed for as long as that token is valid: up to six months for a refresh token. So Identity stores its signing keys in its own PostgreSQL database, encrypts every private key under a key-encryption key (KEK) that never enters the database, and rotates each key automatically every 30 days. Keys are kept and deleted by time, never by count.

## Decision

- **Two purposes, separate keys.** A **published** key signs access and ID tokens, and its public key appears in the JWKS. A **refresh** key signs refresh tokens. Only Identity verifies those, so refresh keys are never published. Each purpose has its own key per algorithm in use; refresh keys are always ML-DSA-65.
- **One row per key, with four instants.** Each key has a `kid`, `alg`, `purpose`, its public key, and four instants that make up its lifecycle:

  | Instant | From then on |
  | --- | --- |
  | `publish_from` | a published key appears in the JWKS |
  | `sign_from` | the key signs new tokens |
  | `sign_until` | the key signs nothing; its private key is deleted |
  | `publish_until` | the key no longer verifies anything and the row is deleted |

  At any instant, exactly one key per purpose and algorithm signs. The application chooses it by `sign_from <= now < sign_until`; the JWKS lists published keys with `publish_from <= now < publish_until`.
- **Private keys encrypted with AES-256-GCM.** The private key is stored as its PKCS#8 encoding, encrypted under the KEK with a fresh random 12-byte nonce and a 128-bit tag. The associated data binds the ciphertext to its row: a length-prefixed encoding of the purpose, `kid`, `alg`, KEK ID and public key. A ciphertext that fails to decrypt is an error, never a reason to try another key.
- **The KEK comes from configuration, never from the database.** It is 32 random bytes with an ID, supplied as a secret (a file or environment variable from devenv in the lab; never committed). Identity may hold older KEKs for decryption only. Rotating the KEK re-encrypts every remaining private key under the new one, and each row records the ID of the KEK it uses.
- **Public rows are protected by a MAC.** A writer to the database who does not know the KEK could otherwise insert a public key of their own, or extend a key's `publish_until`, and every consumer would trust the token it signs. Each row therefore carries an HMAC-SHA256 over `kid`, `alg`, `purpose`, the public key and the four instants. Its key is derived from the KEK with HKDF and a dedicated `info` value. Rows with an invalid MAC are neither published nor used, and they are logged as an error.
- **Private keys live only as long as they sign.** When `sign_until` passes, the private key is deleted and the row keeps only public data. At any moment at most two private keys exist per purpose and algorithm: the one that signs and the next one.
- **Rotation every 30 days, with overlap.** A new key is created with `publish_from = now`, `sign_from = now + JWKS cache lifetime` (1 hour), and `sign_until = sign_from + 30 days`. A key stays until every token it signed has expired: `publish_until = sign_until + longest lifetime of the tokens it signs + 30 seconds of skew`. For published keys that lifetime is minutes; for refresh keys it is the absolute refresh lifetime of six months, so up to nine refresh keys exist at once, all but two of them public only.
- **The first key signs immediately.** When no key for a purpose and algorithm exists, nobody can hold a token signed by it yet, so it is created with `publish_from = sign_from = now`.
- **One rotation use case, run by a schedule.** Rotation is a use case that can be run by hand, for example after a compromise. A schedule runs it daily and it creates a key only when the next one is due. It takes a PostgreSQL advisory lock, so several Identity instances never create two keys for the same slot. Creating, retiring and deleting a key records an audit event ([ADR 0006](0006-audit-events-recorded-by-each-service.md)).
- **JWKS cached for one hour.** The JWKS response sets `Cache-Control: max-age=3600`. Consumers refresh it when that expires, or when a token names a `kid` they do not know, at most once a minute.
- **Compromise ends a key at once.** A compromised key is deleted together with its row, and a new one is created to sign immediately. Tokens it signed stop verifying; that is intended.

## Considered options

- **Private keys in a KMS, HSM or Vault Transit**, signing remotely so they never enter the JVM. It is the strongest option, but `TokenSignerPort` would call an external service instead of signing with a supplied key, and ML-DSA-65 support is not available everywhere. Worth reconsidering for the product, not needed for the lab.
- **Private keys unencrypted**, relying on database access control or disk encryption. Rejected: every dump, backup or SQL read would expose them.
- **Encryption in the database with pgcrypto.** Rejected: the KEK would travel in SQL statements and could appear in logs and statement statistics.
- **A PKCS#12 keystore file beside the application.** Rejected: its password sits in the same configuration, and several instances need a shared file.
- **Keys generated in memory at startup.** Rejected: every restart invalidates every token, and each instance would sign with a different key.
- **A fixed number of keys per algorithm**, for example six, deleting the oldest. Rejected: six months span six or seven 30-day rotations, plus the signing and the next key, so a count cut short deletes keys whose refresh tokens are still valid.
- **Refresh tokens signed with published keys.** Rejected: every retired key would stay in the JWKS for six months, about nine ML-DSA-65 keys of 2.6 KB each, for tokens no consumer ever verifies.
- **Rotation every 90 days.** Fewer keys, but a monthly rotation exercises the procedure often enough that an emergency rotation is routine.

## Consequences

- Identity needs a database before it can sign anything; it gains PostgreSQL, migrations and a persistence adapter that replaces `EmptyJwksRepository`.
- Losing the KEK makes every private key unreadable. Identity then creates new keys, and every token issued before stops verifying, including refresh tokens.
- The JWKS holds about two to three keys per algorithm in use, around 8 KB with ML-DSA-65.
- The rotation tests depend on the clock from `libs/clock`, so a 30-day rotation is tested without waiting 30 days.
