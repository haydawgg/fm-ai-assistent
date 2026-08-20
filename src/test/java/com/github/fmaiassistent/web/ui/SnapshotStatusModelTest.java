package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotStatusModelTest {
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void identifiesPartialSeasonStatisticsWithoutTreatingThemAsEmpty() {
        SnapshotStatusModel model = SnapshotStatusModel.from(Map.of(
                "loaded_at", "2026-08-20T11:30:00Z",
                "season_key", "2025/26",
                "season_stats_state", "partial"), 100, NOW);

        assertEquals(WorkspaceLoadState.PARTIAL, model.state());
        assertEquals("2025/26", model.season());
        assertEquals(100, model.playerCount());
    }

    @Test
    void staleSnapshotsRemainUsableButAreClearlyMarked() {
        SnapshotStatusModel model = SnapshotStatusModel.from(Map.of(
                "loaded_at", "2026-08-19T08:00:00Z"), 12, NOW);

        assertEquals(WorkspaceLoadState.STALE, model.state());
        assertEquals(true, model.usable());
    }
}
