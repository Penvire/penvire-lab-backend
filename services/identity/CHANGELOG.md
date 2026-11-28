# Changelog

## 0.1.0 (2026-11-28)


### Features

* **audit:** generate audit manifests and comment their changes on pull requests ([#47](https://github.com/Penvire/penvire-lab-backend/issues/47)) ([1b796d8](https://github.com/Penvire/penvire-lab-backend/commit/1b796d8bb0534e36923dee6c3f9a0730250b672b))
* **build:** share Java analysis, formatting and telemetry conventions ([e9372ad](https://github.com/Penvire/penvire-lab-backend/commit/e9372ad00226a0b32adfb997c0ef1f765ba62594))
* **clock:** add a shared clock library with a movable test clock ([e6ee594](https://github.com/Penvire/penvire-lab-backend/commit/e6ee5940fd526ecaa11a5f6ca2a67387e91bbdca))
* **dev:** add local TLS gateway and observability stack ([e9372ad](https://github.com/Penvire/penvire-lab-backend/commit/e9372ad00226a0b32adfb997c0ef1f765ba62594))
* **identity:** add Gradle build and identity service skeleton ([abaab8b](https://github.com/Penvire/penvire-lab-backend/commit/abaab8be2bea9dde1ee0d761ef1e2107c5926192))
* **identity:** add OpenID discovery and JWKS endpoints ([#35](https://github.com/Penvire/penvire-lab-backend/issues/35)) ([9d5dcf9](https://github.com/Penvire/penvire-lab-backend/commit/9d5dcf9d14375b2f1cccec591ba7bce91d7a54f9))
* **identity:** issue ML-DSA-65 access tokens ([#24](https://github.com/Penvire/penvire-lab-backend/issues/24)) ([8a7ba65](https://github.com/Penvire/penvire-lab-backend/commit/8a7ba65b420b90751320329d12eb7b1214cb3f7a))
* **identity:** record audit events for account and signing key rotation lifecycle ([00e2eae](https://github.com/Penvire/penvire-lab-backend/commit/00e2eae81218f72f5688e926b615d65e2498050a))
* **identity:** rotate signing keys ([#54](https://github.com/Penvire/penvire-lab-backend/issues/54)) ([b9f784a](https://github.com/Penvire/penvire-lab-backend/commit/b9f784a98ef8f57e4d28a3efe9bf9290168ef84a))
* **identity:** rotate signing keys on startup and schedule ([#56](https://github.com/Penvire/penvire-lab-backend/issues/56)) ([9378b8c](https://github.com/Penvire/penvire-lab-backend/commit/9378b8c08ee562968c5af5fc2a955b76ad450b8c))
* **identity:** serve health on the management port ([abaab8b](https://github.com/Penvire/penvire-lab-backend/commit/abaab8be2bea9dde1ee0d761ef1e2107c5926192))
* **identity:** sign access tokens with the stored signing key ([#53](https://github.com/Penvire/penvire-lab-backend/issues/53)) ([6aacca7](https://github.com/Penvire/penvire-lab-backend/commit/6aacca7f9693c7e8612e6bd4c0b703766c4950eb))
* **identity:** store signing keys encrypted in Identity's database ([#50](https://github.com/Penvire/penvire-lab-backend/issues/50)) ([db0ff18](https://github.com/Penvire/penvire-lab-backend/commit/db0ff181af3104647fcf71b64e28fb04dee4aaaf))
* **identity:** store user accounts and verify passwords with Argon2id ([00e2eae](https://github.com/Penvire/penvire-lab-backend/commit/00e2eae81218f72f5688e926b615d65e2498050a))
* **identity:** support issuing RS256 access tokens ([#57](https://github.com/Penvire/penvire-lab-backend/issues/57)) ([715e267](https://github.com/Penvire/penvire-lab-backend/commit/715e267e08e4253db9ceab69b4bea21e04d71393))
* **security:** add Bearer JWT authentication and cached JWKS resolution ([#58](https://github.com/Penvire/penvire-lab-backend/issues/58)) ([9e3fec6](https://github.com/Penvire/penvire-lab-backend/commit/9e3fec6e526a0136be7c6008823ed3208e96bbef))


### Bug Fixes

* **grafana:** show zero 5xx rates when no error series exists ([e9372ad](https://github.com/Penvire/penvire-lab-backend/commit/e9372ad00226a0b32adfb997c0ef1f765ba62594))
* **identity:** enforce access-token size limits ([#25](https://github.com/Penvire/penvire-lab-backend/issues/25)) ([fdf5112](https://github.com/Penvire/penvire-lab-backend/commit/fdf511268cc16a9473c3442cfd3eb4241da903a4))
* **identity:** fix password reuse limit, credential checks, and revoke timestamp ([#67](https://github.com/Penvire/penvire-lab-backend/issues/67)) ([c334291](https://github.com/Penvire/penvire-lab-backend/commit/c334291fbb8123cf2ce5be7f77875f4248e42de9))
* **persistence:** keep UUIDv7 IDs ordered when the clock steps back ([e6ee594](https://github.com/Penvire/penvire-lab-backend/commit/e6ee5940fd526ecaa11a5f6ca2a67387e91bbdca))
