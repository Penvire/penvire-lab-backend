# OpenSSL ML-DSA-65 fixture

Generated with OpenSSL 3.5.9 (29 Sep 2026), independently of the JDK verifier.
`ml-dsa-65.jwt` is a compact access token. `ml-dsa-65-public.der` is the matching
X.509 SubjectPublicKeyInfo. The fixture clock is 2026-01-01T00:01:00Z.

The signing input is the ASCII text of the first two compact segments, including
their separating dot and no final newline. The decoded JSON preserves whitespace
and member order chosen for the fixture. The signature is pure ML-DSA-65 with
empty context, as specified by [RFC 9964 §5](https://www.rfc-editor.org/rfc/rfc9964.html#section-5).

The fixture was produced by base64url-encoding the UTF-8 objects below without
padding and joining them with a dot in `signing-input.txt`:

```json
{ "typ": "at+jwt", "kid": "ml-dsa-65-test", "alg": "ML-DSA-65" }
{ "sub": "fixture-user", "iss": "https://issuer.example", "aud": "fixture-api", "jti": "mldsa-1", "client_id": "fixture-client", "iat": 1767225600, "nbf": 1767225600, "exp": 1767225900 }
```

```sh
openssl genpkey -algorithm ML-DSA-65 -out fixture-private.pem
openssl pkey -in fixture-private.pem -pubout -outform DER -out ml-dsa-65-public.der
openssl pkeyutl -sign -rawin -inkey fixture-private.pem -in signing-input.txt -out signature.bin
openssl pkeyutl -verify -rawin -pubin -inkey ml-dsa-65-public.der -keyform DER -in signing-input.txt -sigfile signature.bin
```

OpenSSL reported `Signature Verified Successfully`. The 3,309-byte signature was
base64url-encoded without padding and appended to the signing input after a dot.
The conversion from standard Base64 is `tr '/+' '_-' | tr -d '='`.
Only the public key and compact token are stored here. Regeneration yields a new
key and signature.
