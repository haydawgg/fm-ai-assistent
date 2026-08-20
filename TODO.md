# FM AI Follow-up TODO

Execution status for the current-season statistics, filtering, import, and player-desk performance milestone.

## 1. Validate a real FM26 build

- [x] Capture the installed `game_plugin.dll` path, size, SHA-256, and FM26 file version. The numeric live offset-table build key still requires a running process/capture.
- [blocked] Validate candidate native fields against visible in-game values: Source UID, morale, condition, guide value, and transfer value. The readers and validation seams exist, but no live capture is available here.
- [blocked] Discover and validate the current-season statistics pointer/block layout. No unverified offsets are registered.
- [x] Implement fail-closed build/hash profile selection, plausible-range validation, zero preservation, and relationship checks.
- [x] Add a side-effect-free candidate-field diagnostic harness and field-level provenance for UI/MCP output.
- [x] Persist the numeric native offset-table build key with each published snapshot.
- [blocked] Register a native statistics layout and add a real-build/captured-memory regression fixture. This is intentionally deferred until the capture is available.
- [x] Confirm unsupported builds and unreadable pointers still load players successfully through automated fail-closed tests.

## 2. Make CSV import safer and more useful

- [x] Add an import preview dialog before persistence.
- [x] Display total, valid, invalid, matched, ambiguous, and unmatched row counts.
- [x] Show the exact player match for every accepted row.
- [x] Allow cancellation before database changes are committed.
- [x] Add explicit season and scope selection when the CSV does not contain them.
- [x] Support common FM column aliases plus comma, semicolon, tab, quoting, decimal-comma, blank, and zero values.
- [x] Normalize common advanced CSV metrics and derive conservative aggregate per-90/starts-rate values.
- [x] Match through a database-side name query with normalized club disambiguation; avoid full-table loading.
- [x] Preserve source filename, import timestamp, season, and scope in metadata.
- [x] Add an import history/status view and persisted import history.

## 3. Expose imported and match-level statistics

- [x] Add repository/service queries for imported player metrics.
- [x] Add repository/service queries for match-level statistics by player, season, date, competition, and opponent.
- [x] Add imported extra metrics and source metadata to the player dossier.
- [x] Add recent match-stat summaries to the player dossier.
- [x] Include imported metrics, source labels, and null-safe season metadata in MCP compact and full player maps.
- [x] Add `fm26_get_player_match_stats` MCP lookup support.
- [x] Clearly label native RAM, aggregate CSV, and match CSV data sources.
- [x] Keep unavailable values as `null`; never convert them to zero.
- [x] Document that competition breakdowns are import-backed until native support exists.

## 4. Complete test coverage

- [x] Test exact build/hash profile selection and build fallback.
- [blocked] Test native candidate values against captured memory fixtures. Requires a real capture.
- [x] Test unsupported builds and unreadable pointers.
- [x] Test CSV comma, semicolon, tab, quoting, decimal-comma, blank, and zero values.
- [x] Test impossible relationships such as goals or assists exceeding minutes.
- [x] Test unique name matching, club disambiguation, ambiguous names, and unmatched rows.
- [x] Test blank CSV values do not erase existing trusted values.
- [x] Test aggregate-stat and match-stat persistence paths.
- [x] Test import replacement/history behavior and transactional rollback boundaries.
- [x] Test shared UI/MCP filter semantics through the shared query/specification path.
- [x] Test unknown values remain null and render as `Unknown` in the UI formatting path.
- [x] Keep the full Maven test suite green.

## 5. Finish player-desk performance work

- [x] Use database-side count and page queries for player-desk requests.
- [x] Keep sorting database-side for supported grid columns.
- [x] Keep scalar, status, contract, and performance filtering database-side.
- [x] Configure the Vaadin lazy data provider with a page size near 100.
- [x] Remove remaining full-table player loading from normal desk views.
- [x] Add indexes for club, playing club, CA, PA, contract expiry, appearances, starts, minutes, goals, assists, rating, and numeric age.
- [x] Cache stable filter-option lists and evict them after snapshot publication.
- [blocked] Measure load time and heap usage on a large real player snapshot. Requires representative FM26 data and a runtime measurement environment.

## Definition of done

- [blocked] A validated FM26 build provides trustworthy native current-season statistics; the code is ready, but no supported live build/capture was available.
- [x] Unsupported or unreadable layouts never block player loading.
- [x] CSV imports are previewable, auditable, and conservative.
- [x] Aggregate and match-level statistics are queryable in the UI and MCP.
- [x] UI and MCP filtering use the shared database-side semantics.
- [x] Large snapshots use paging without loading every player into normal desk memory.
- [x] `mvn test` passes and release notes document remaining build limitations.

## Next external input

Provide one FM26 capture containing the exact plugin identity/build number and a player whose visible values include current-season totals. That capture is the only remaining input needed to replace the fail-closed native statistics registry with a verified build-specific layout.
