# FM26 offset inventory and expansion options

Research date: 2026-08-20

This note separates offsets already used by FM AI from externally published FM26
offsets. External values are evidence for investigation, not production-ready
layouts. They are version-specific and must be validated against a live build
before being enabled.

## Local FM26 build evidence

The installed FM26 files were inspected on 2026-08-20. The game process was
not running, so this is file identity evidence only; it does not validate a
live save's pointers or player/statistics values.

| Artifact | Value |
|---|---|
| `game_plugin.dll` | `C:\Program Files (x86)\Steam\steamapps\common\Football Manager 26\fm_Data\Plugins\x86_64\game_plugin.dll` |
| Plugin file version | `26.3.2` |
| Plugin product version | `26.3.2+2329565` |
| Plugin size | `514,894,848` bytes |
| Plugin SHA-256 | `eb6c86fab56e051fe41482c7b93d8cb0af716a1cc2c6197ce1babbff68372bb8` |
| `fm.exe` product version | `6000.0.52f1-fm26-05f1 (87a0370e9917)` |

The numeric offset-table build key used by `FmOffsets` cannot be inferred
reliably from the Windows file version alone. A live process capture remains
required before registering a statistics layout or changing the fail-closed
profile registry.

## What FM AI currently has

### Native table and build offsets

[`FmOffsets.java`](src/main/java/com/github/fmaiassistent/linux/FmOffsets.java)
contains 48 build-to-offset-table RVAs. The default build is `0x238bdd`, with
table RVA `0x4e49490`. The named table slots are:

| Table | Slot offset |
|---|---:|
| City | `0x0F0` |
| Club | `0x0F8` |
| Competition | `0x100` |
| Continent | `0x108` |
| Nation | `0x110` |
| Currency | `0x118` |
| People | `0x150` |
| Region | `0x160` |
| Stadium | `0x168` |
| Team | `0x180` |
| Agreement | `0x1A0` |

The table traversal is `tableBase + slot -> slot pointer + 0x80 -> start/end
array`. Counts are range-checked before iteration. The current-date RVA is
directly known only for `0x238bdd` (`game_plugin.dll + 0x4df3c18`); other known
builds use an estimated RVA derived from the table-RVA gap.

### Player/person layout already used

The central record is treated as a person object. The current code reads:

- CA at `record - 0x24`, PA at `record - 0x22`.
- Home/current/world reputation at `record - 0x2A`, `-0x28`, `-0x26`.
- Attribute history source at `record - 0x13A`, with position and visible fields
  decoded from the copied byte block.
- Height at `record - 0x5A`, date of birth at `record + 0x88`, gender at
  `record + 0x19`.
- Contract pointer at `record + 0xA8`; wage at `contract + 0x20`, expiry at
  `contract + 0x48`, and transfer flags at `contract + 0x57`.
- Contracted club through `contract + 0x10 -> team + 0x30`; playing-club
  fallback through `record - 0x158 -> team + 0x30`.
- Future-transfer block at `contract + 0xD8`, with club `+0x30`, date `+0x10C`,
  contract-end date `+0x110`, and activity/sentinel checks at `+0x100/+0x104`.
- Injury reference and flag at `record - 0x190/-0x18C`.
- Preferred-move traits are discovered in a bounded `record - 0x1C0` to
  `record + 0x1C0` scan; the preferred trait object uses a `+0x40` pointer
  relationship.

Club, competition, finance, and tactic readers also have dedicated relative
offsets. They are listed in `ClubExporter.java`, `CompetitionExporter.java`,
and `TacticExporter.java` rather than in one shared layout registry.

### Season statistics status

[`BuildSeasonStatsReader.java`](src/main/java/com/github/fmaiassistent/exporter/BuildSeasonStatsReader.java)
has the correct safety seam but its default layout map is empty. A layout must
be supplied per build before any statistics are read. Candidate blocks are
validated using the player pointer, season start, plausible ranges, and
`goals <= minutes`; unreadable fields stay `NULL`.

That is the correct current posture: the repository has no verified production
offset for current-season appearances, starts, minutes, goals, assists, or
average rating.

## What is publicly documented elsewhere

### FMSuperScout: a useful, version-pinned native layout

The open-source [FMSuperScout `Fields.cs`](https://raw.githubusercontent.com/mavarobli/FMSuperScout/main/plugin/Fields.cs)
documents offsets attributed to decompiled Cheat Engine tables and explicitly
pinned to `game_plugin.dll` FM 26.3.x. Its player block includes:

- Dynamic person-to-player offsets: `0x288` for a pure player and `0x380` for
  a player/staff object.
- Player CA/PA at player-block `0x264/0x266`.
- Player condition and morale at `0x258/0x26C`.
- Player guide/transfer values at `0x234/0x238`.
- Person DOB, contract pointer, and hidden personality fields at the same
  offsets that correspond closely to FM AI's existing person layout.
- Contract wage, expiry, squad number, and status flags at `0x20/0x48/0x5D/0x57`.
- Team competition pointers at `0x50`/`0x60`, and a team schedule pointer at
  `0xA0` with date candidates at schedule `0x94`/`0x18`.

The mapping is promising because the published player block offset `0x288`
explains why its player-block CA `0x264` corresponds to person-relative
`-0x24`, which is already used by FM AI. It also predicts two immediate
candidate fields in our person coordinate system:

- morale: `record - 0x1C` (`0x26C - 0x288`)
- condition: `record - 0x30` (`0x258 - 0x288`)

These are candidates, not yet validated FM AI offsets. The same source marks its
loan-listed bit as not yet verified in-game, and its schedule date is the next
match date, not necessarily the exact world date between matches. Neither
should be promoted to authoritative data without corroboration.

The accompanying [FMSuperScout memory reader](https://raw.githubusercontent.com/mavarobli/FMSuperScout/main/plugin/MemScan.cs)
is also relevant architecturally: it uses safe `ReadProcessMemory`, readable
region checks, module-image caching, and an optional Windows process snapshot.
Those ideas are useful for improving consistency of a long FM AI export.

### FM26PlayerExport: UI export, not raw offsets

The open-source [FM26PlayerExport README](https://github.com/contatovinteset-wq/fm26-editor-workspace/blob/main/fm26-player-export/README.md)
describes a BepInEx IL2CPP plugin that captures the visible, virtualized player
table while scrolling. Because it reads UI cells and headers, the user can
choose the columns in FM without the app knowing raw memory offsets. Its
[player handler](https://raw.githubusercontent.com/contatovinteset-wq/fm26-editor-workspace/main/fm26-player-export/Handlers/PlayerExportHandler.cs)
delegates to a generic table exporter.

The same plugin includes a [match-statistics handler](https://raw.githubusercontent.com/contatovinteset-wq/fm26-editor-workspace/main/fm26-player-export/Handlers/MatchStatsExportHandler.cs)
that visits Key Statistics, Passing, Attacking, Defending, Goalkeeping, and
Set Pieces tabs. This is a route to richer match-level data, but it is not a
verified pointer to current-season aggregates.

### Other public analysis workflows

The [FM26 editor workspace guidance](https://github.com/contatovinteset-wq/fm26-editor-workspace/blob/main/SKILL%20FM26.md)
describes IL2CPP metadata dumping with Il2CppDumper and explicitly presents
example pointer offsets as placeholders. It is useful as a discovery method,
not as a source of production values.

The [FMdataExport analyzer](https://github.com/fredGob/FMdataExport/blob/main/DataAnalyser/FM26_dataAnalyser.html)
shows the breadth available through configured CSV exports: minutes, FM rating,
goalkeeper metrics, defensive actions, passing, xA, chances created, shots,
xG, goals per 90, distance, and sprints. These fields demonstrate the value of
a CSV/UI import fallback, but the analyzer does not provide raw memory offsets.

## What FM AI can safely add

### Near-term native candidates

1. Add a build-layout registry keyed by `game_plugin.dll` file version and/or
   module hash in addition to the existing internal build ID.
2. Add morale, condition, guide value, transfer value, and person UID as
   candidate fields behind validation. Require multiple known players, range
   checks, and a version match before publishing them.
3. Add provenance metadata: module version/hash, layout ID, field-level state,
   validation sample count, and read source. This lets the UI and MCP explain
   why a value is unknown.
4. Improve game-date handling with a clearly labelled approximate team-schedule
   fallback. Do not use an approximate date to validate season statistics.

### Season statistics

Do not copy a guessed stats offset into `BuildSeasonStatsReader`. The practical
next step is a discovery harness that records candidate blocks for a controlled
set of players and compares them against the in-game player profile or a UI
export. A layout should only be enabled after it survives:

- identity and pointer-chain checks;
- current-season checks across July and January;
- zero-value and nonzero-value players;
- impossible-value rejection;
- comparison against at least two independent sources; and
- more than one FM patch/build.

If this cannot be completed, implement CSV/UI import as a fallback. Merge the
export into the RAM snapshot using UID where present, otherwise a conservative
name/club/date match with an explicit `imported` provenance. This can deliver
current-season totals and the richer per-90 fields without fabricating memory
values.

### Longer-term data model

For match-level analysis, store a separate match-stat row keyed by player,
match, competition, and snapshot date. Aggregate current-season totals in SQL
from those rows only when coverage is complete. Keep the current nullable core
fields as the fast summary, and never mix UI-imported and raw-memory values
without preserving their source and timestamp.

## Bottom line

The public 26.3.x layout is enough to prioritize validation of morale, condition,
transfer value, UID, and version detection. It is not enough to enable the
season aggregate reader. The best product path is a dual-source design:
validated native offsets for stable identity/contracts/attributes, plus an
optional UI/CSV import path for current-season and detailed performance data.
