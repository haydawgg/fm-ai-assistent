# FM AI Assistent Bug Report

Analysis date: 2026-08-18
Reviewed commit: `270e93a`
Scope: current project, including the latest UI/demo/security/Electron changes and existing runtime paths.

Status: all listed findings fixed in the working tree.

## Findings

### Fixed — Electron adopts an unrelated HTTP 5xx service

- **Location:** `electron/java-launcher.js:139-149`
- **Impact:** Any service returning HTTP 500 or higher on the configured backend port is treated as the FM AI backend. Electron can load an unrelated local service instead of starting FM AI or reporting a port conflict.
- **Reproduction:** Run another HTTP service on port 8080 that returns a 500 response, then launch the Electron app.
- **Fix direction:** Verify FM AI/Vaadin identity for 5xx responses before marking the service external.

### Fixed — Electron readiness polling accepts any unrelated 2xx service

- **Location:** `electron/main.js:87-94`, `electron/java-launcher.js:139-149`
- **Impact:** The initial port probe checks response identity, but the later readiness poll accepts any successful HTTP response. A service that takes over the port during startup can be loaded as FM AI.
- **Reproduction:** Start the app while port 8080 is free, then make an unrelated service return HTTP 200 before the Java backend becomes ready.
- **Fix direction:** Apply the same backend identity check during readiness polling.

### Fixed — Chat retry actions accumulate duplicate listeners

- **Location:** `src/main/java/com/github/fmaiassistent/web/ui/ChatView.java:1640-1671,1930-1940`
- **Impact:** Each displayed error adds another listener to the same Retry button. After repeated failures, one click can issue multiple retry requests and create concurrent streams.
- **Reproduction:** Cause several chat requests to fail, then click Retry once on the latest error.
- **Fix direction:** Register one stable listener or replace the previous listener before adding a new one.

### Fixed — Demo FC Porto is linked to Eredivisie

- **Location:** `src/main/java/com/github/fmaiassistent/service/DemoDataService.java:56-61`
- **Impact:** Porto is labelled `Primeira Liga` but its `CompetitionEntity` is `eredivisie`; relationship-based queries and competition filters classify the club incorrectly.
- **Reproduction:** Enable `app.ui.demo-data=true`, load the demo data, and filter clubs or players by `Primeira Liga`.
- **Fix direction:** Create/use a `Primeira Liga` competition entity for Porto.

### Fixed — “Skip for now” permanently disables onboarding

- **Location:** `src/main/java/com/github/fmaiassistent/web/ui/SettingsDialog.java:164-174`
- **Impact:** Skipping still records completion, but Settings now provides a `Run setup again` action that clears the completion flag and reloads the UI into onboarding.
- **Reproduction:** On a fresh profile, click `Skip for now`, reload, and inspect the top bar and Settings dialog.
- **Fix:** Added `Run setup again` to Settings and corrected the onboarding copy to point users there.

### Fixed — Overview “Contract watch” counts every contract date

- **Location:** `src/main/java/com/github/fmaiassistent/web/ui/OverviewView.java:115-129,246-249`
- **Impact:** The overview counts every nonblank contract date as expiring, including contracts years away. This conflicts with the 180-day actionable window used by `SquadAdvice.contractQueue`.
- **Reproduction:** With the bundled demo data, contracts ending in 2028 are still counted as tracked by the overview despite no actionable 180-day contract entries.
- **Fix:** Reused `SquadAdvice.daysUntilExpiry` and counted only contracts due in 0–180 days.

### Fixed — Browser-safe highlighter suppresses language highlighting

- **Locations:** `src/main/frontend/js/highlight-browser.js:19-25`, `src/main/java/com/github/fmaiassistent/web/ui/ChatView.java:1826-1835`
- **Impact:** The adapter only escapes code and adds the `hljs` marker, but ChatView then marks the block handled and returns. The existing Java/JSON/SQL fallback never runs, so fenced code renders without syntax coloring.
- **Reproduction:** Render a chat response containing fenced Java, JSON, or SQL code and observe that it is plain escaped text.
- **Fix:** The adapter now marks itself as fallback-only, allowing ChatView’s language-specific fallback to execute.

## Verification

- Full Maven test suite passed with the bundled Maven 3.9.16 installation.
- Electron JavaScript syntax checks passed.
- Source fixes were applied across Electron, chat, onboarding, demo data, overview metrics, and frontend highlighting.
- No Critical findings were confirmed.
