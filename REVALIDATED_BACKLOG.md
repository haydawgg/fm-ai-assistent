# Revalidated Hardening Backlog

This file records findings that were reviewed while hardening the Windows desktop release. Critical/high items that are addressed in code are documented in the change history; the remaining medium/low items are deliberately tracked instead of silently disappearing.

## Deferred medium/low follow-up

- Replace remaining `lower(column)` filters with indexed search strategy only after a production-sized benchmark; the H2 query-plan check showed the current `lower(name)` predicate is not a clean equality seek.
- Remove duplicate dependency references emitted by the Vaadin/Electron packaging graph.
- Revisit native-image support only after Windows jar/Electron behavior is stable and native integration tests exist.

## Revalidation rule

Each item must include a reproducible test or query-plan measurement before implementation. Historical report counts are not treated as current defects without that evidence.
