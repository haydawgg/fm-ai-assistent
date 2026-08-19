# Revalidated Hardening Backlog

This file records findings that were reviewed while hardening the Windows desktop release. Critical/high items that are addressed in code are documented in the change history; the remaining medium/low items are deliberately tracked instead of silently disappearing.

## Deferred medium/low follow-up

- Replace remaining `lower(column)` filters with indexed search strategy only after a production-sized benchmark; the H2 query-plan check showed the current `lower(name)` predicate is not a clean equality seek.
- Remove duplicate dependency references emitted by the Vaadin/Electron packaging graph. The warning is reproducible during `npm run build-win`, but the current packaging config excludes `node_modules` from the artifact and produces a successful runnable build; keep this as packaging-noise cleanup rather than a release blocker.
- Revisit native-image support only after Windows jar/Electron behavior is stable and native integration tests exist.

## Completed in the current hardening pass

- Consolidate desktop-view notifications behind `UiFeedback`, preserving existing duration, position, and severity variants.
- Bound chat message, reasoning, and tool-trace CLOB inputs at both the Vaadin composer and persistence/update boundaries; regression coverage is in `ChatContentLimitsTest`.
- Bound chat-session search results at the repository boundary while preserving database-side matching and updated-time ordering; regression coverage is in `ChatSessionPersistTest`.

## Revalidation rule

Each item must include a reproducible test or query-plan measurement before implementation. Historical report counts are not treated as current defects without that evidence.
