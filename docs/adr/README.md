# Architecture decision records

Decisions for all Penvire Lab repositories (backend, contracts, clients) are recorded here.
Record a decision only when it is hard to reverse, surprising without context and the result of a real trade-off.

## Lifecycle

Each ADR carries a `Status` in its frontmatter:

- `accepted`: the decision applies. Write it in the pull request itself: merging is the acceptance, and a rejected ADR's pull request is closed.
- `proposed`: only for an ADR merged on purpose before it is decided; rare.
- `deprecated`: no longer applies and has no replacement.
- `superseded by ADR-NNNN`: replaced by a later ADR.

Do not rewrite an accepted ADR. To change a decision, write a new ADR and set the old one to `superseded by ADR-NNNN`. Fixing typos and broken links is fine.

## Numbering

Files are named `NNNN-short-slug.md` with four-digit sequential numbers. Take the highest existing number and add one. Numbers are never reused.

## Template

```md
---
status: accepted
date: YYYY-MM-DD
---

# Short title of the decision

1-3 sentences: the context, the decision and why.
```

Add `## Considered options` only when the rejected alternatives are worth remembering, and `## Consequences` only for downstream effects that are not obvious.

## Index

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-versioned-contracts-through-published-packages.md) | Versioned contracts through published packages | accepted |
| [0002](0002-client-access-through-bff-and-public-api.md) | Client access through the BFF and the public API | accepted |
| [0003](0003-events-through-a-transactional-outbox.md) | Events through a transactional outbox, as CloudEvents with Avro payloads | superseded by ADR-0004 |
| [0004](0004-events-written-with-the-latest-revision-of-their-package.md) | Events through a transactional outbox, written with the latest schema revision of their package | accepted |
| [0005](0005-event-library-relay-consumer-and-migrations.md) | Event library: one relay per service, SmallRye consumers, and migrations shipped by the library | accepted |

Add every new ADR to this index in the same pull request.
