# Domain docs

## Layout

This repository holds the domain documentation for all Penvire Lab repositories:

- `GLOSSARY.md` at the repository root for domain vocabulary.
- `docs/adr/` for architecture decision records.

## Before exploring

Read `GLOSSARY.md` when present, then `docs/adr/README.md` and the ADRs relevant to the work.

If domain documentation is missing, proceed silently.
Do not flag its absence or suggest creating it upfront.
The `domain-modeling` skill creates documentation lazily when terms or decisions are resolved.

## Vocabulary

Use glossary terms in issue titles, proposals, hypotheses and test names.
Avoid synonyms that the glossary explicitly rejects.
If a needed concept is absent, reconsider the term or note the gap for `domain-modeling`.

## Decisions

Surface conflicts with existing ADRs explicitly, including the ADR identifier and the reason for reopening the decision.
When recording decisions, follow the lifecycle, numbering, template and index rules in `docs/adr/README.md`.
