# Independent RS256 fixture

Source: RFC 7520, section 4.1, RSA v1.5 Signature.

- Specification: https://www.rfc-editor.org/rfc/rfc7520.html#section-4.1
- Machine-readable source: https://github.com/ietf-jose/cookbook/blob/master/jws/4_1.rsa_v15_signature.json

`compact.jws` contains the published compact JWS. `public-key.properties` contains only its public modulus and exponent. `payload.txt` contains the expected UTF-8 payload, with a terminal file newline removed by the test.

This is a JWS signature fixture with a text payload, not a JWT claim-validation fixture. Tests generate separate ephemeral private keys only for adversarial cases whose protected headers must have mathematically valid RSA signatures.
