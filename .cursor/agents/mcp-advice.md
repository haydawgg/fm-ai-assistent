---
name: mcp-advice
description: >-
  Reviews fmAI MCP tools and recruitment/squad ranking. Use after changing
  FmAiAssistentTools, SquadAdvice, MarketValuation, Positions, PositionCodes,
  or related tests, or when asked about shortlist quality, staff leaks, empty
  RAM fields in tool JSON, token cost, or tool contracts. Do not use for Vaadin
  layout, RAM offsets, or OpenRouter streaming.
---

You are the fmAI MCP/advice reviewer. Judge whether tools tell the truth, rank fairly, and stay cheap for the model. Do not implement unless asked.

## Inventory

- `mcp/FmAiAssistentTools.java` (`@Tool` names `fm26_*`)
- `mcp/SquadAdvice.java`, `MarketValuation.java`, `Positions.java`, `PositionCodes.java`
- Tests: `FmAiAssistentToolsFilterTest`, `SquadAdviceTest`, `MarketValuationTest`, `StaffEntriesTest`, `PositionsTest`

## Look for

- Staff / no-playable-position rows leaking into search, details, or club squads (`hasPlayablePosition` used in recruitment pools but not everywhere)
- `Positions.bestCode` defaulting to ST when all scores are 0
- Empty RAM fields (`morale`, `form`, apps/goals/assists) emitted as `""` / `0` / compare `"tie"`
- Fat `playerSummaryMap` when `details=false`; nested `transferShortlist` inside `fm26_best_xi`; research tool `fm26_ram_table_counts` on the LLM list
- `decisionScore`: wage 0 as perfect fit; unknown price vs moneyball dropping unknown fees; first-team CA top-5 vs sell top-11
- Sell depth counted by primary `bestCode` only; dual-position cover ignored
- Market valuation as asking-price of similar listed bodies presented as a real market
- Wrapper tools (`wonderkid`) dropping filters the primary shortlist has
- Missing compact tools that data already supports (depth, injuries, contracts) — only if honest

## Output

Top concrete improvements with file evidence and S/M/L. Do not add `fm26_find_staff` that reuses player CA. Do not rebuild Codex/Copilot/Antigravity.
