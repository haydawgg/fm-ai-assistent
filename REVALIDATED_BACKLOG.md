# Revalidated Hardening Backlog

This file records findings that were reviewed while hardening the Windows desktop release. Critical/high items that are addressed in code are documented in the change history; the remaining medium/low items are deliberately tracked instead of silently disappearing.

## Deferred medium/low follow-up

- Replace remaining `lower(column)` filters with indexed search strategy where the query plan shows a measurable cost.
- Split the largest UI modules (`MainView`, `ChatView`) further behind stable domain interfaces after the current behavior is covered by browser smoke tests.
- Consolidate the remaining repeated asynchronous grid-loading paths onto `UiFeedback` and a shared loading seam.
- Add bounded chat-message retention and a migration from floating-point cost storage to a decimal database column.
- Remove duplicate dependency references emitted by the Vaadin/Electron packaging graph.
- Revisit native-image support only after Windows jar/Electron behavior is stable and native integration tests exist.

## Revalidation rule

Each item must include a reproducible test or query-plan measurement before implementation. Historical report counts are not treated as current defects without that evidence.
