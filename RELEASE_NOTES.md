# Release Notes

## 0.4.0-SNAPSHOT (unreleased)

### Most important

- New `fm26_moneyball_shortlist` MCP tool: moneyball value signings, sorted by `signing_rating` (0-100) — a composite of CA, age-adjusted PA and transfer value (fee + 3 years of wages vs the market median). Each candidate carries `deal_tier` (excellent/good/average/overpriced), market value, 3-year total cost and the saving vs market.
- Squad advice: `fm26_sell_shortlist`, `fm26_wonderkid_shortlist`, `fm26_compare_squads`, `fm26_compare_players`, `fm26_best_xi`, plus Squad trim / First XI / optional Chat views. Snapshots persist in file-backed H2 next to the settings file.

### Other features

- Upgraded Spring AI 2.0.0-M2 to 2.0.0 (MCP SDK 0.17.1 to 2.0.0) so Antigravity clients requesting MCP protocol 2025-11-25 connect cleanly instead of hanging on version negotiation.
- MCP instructions now steer value-oriented recruitment (bargains) to `fm26_moneyball_shortlist` and tactical recruitment to `fm26_transfer_shortlist`.
- Moneyball view in the web UI (`/moneyball`, linked from the header): pick a managing club and a position, and every value signing appears ranked by `signing_rating` with `deal_tier` badges, market value, fee, 3-year cost and saving vs market in a sortable grid. It shares the exact rating pipeline with the `fm26_moneyball_shortlist` MCP tool (`FmAiAssistentTools.moneyballRows`).
- Moneyball candidates default to a 40-year age cap (MCP tool and UI alike) so retired/staff entries in the People export no longer top `signing_rating` with "free" £0-wage deals; pass `maxAge` explicitly to override.
- Staff/retired people in the People export are now excluded from transfer and moneyball candidate pools: they carry no position attributes, while real players always do (position-score floor of 5, shared by both tools and the UI).
- The market model (`MarketValuation`) also excludes staff/retired entries when building comparables, so bucket medians are no longer dragged down by £0-wage, near-zero-price staff rows; the position floor is single-source in `MarketValuation.hasPlayablePosition`.
- UI polish: the moneyball view honours the Settings currency instead of hardcoding £ (shared `MoneyDisplay` formatter), quick filters expose proper labels to assistive tech, the Load from RAM button now meets WCAG AA contrast, and the moneyball grid prompts "Pick a club" instead of a misleading empty state.
- Load from RAM is faster: the player memory export reads records in parallel (up to 8 threads) and caches club display names by address (~26s baseline for the full export); the parallel split/merge/filter/sort is covered by `PlayerExporterParallelTest` against a scripted memory stub.

## 0.3.0

### Most important

- Better AI responses which costs less tokens

### Other features

- complete new UI

### Bug fixes

- Fixed youth intake players not shown when filtering (again)
