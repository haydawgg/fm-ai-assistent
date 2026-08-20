package com.github.fmaiassistent.web.ui;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Deep read model for one published snapshot's freshness and trust state. */
public record SnapshotStatusModel(
        WorkspaceLoadState state,
        long playerCount,
        String season,
        String source,
        String readAt,
        String statsState,
        String label,
        String detail) {

    public static SnapshotStatusModel from(Map<String, Object> metadata, long playerCount) {
        return from(metadata, playerCount, Clock.systemDefaultZone());
    }

    static SnapshotStatusModel fromHeartbeat(SnapshotHeartbeat.Status heartbeat, long playerCount) {
        WorkspaceLoadState state = heartbeat.empty() ? WorkspaceLoadState.NO_SNAPSHOT
                : heartbeat.stale() ? WorkspaceLoadState.STALE : WorkspaceLoadState.READY;
        return new SnapshotStatusModel(state, playerCount, "", "FM26 memory", "", "unavailable",
                heartbeat.label(), heartbeat.title());
    }

    static SnapshotStatusModel from(Map<String, Object> metadata, long playerCount, Clock clock) {
        Map<String, Object> safe = metadata == null ? Map.of() : metadata;
        SnapshotHeartbeat.Status heartbeat = SnapshotHeartbeat.from(safe, playerCount, clock);
        String statsState = text(safe.getOrDefault("season_stats_state", "unavailable"));
        String season = text(safe.get("season_key"));
        String source = text(safe.getOrDefault("season_stats_source", "FM26 memory"));
        String readAt = text(safe.get("season_stats_read_at"));
        WorkspaceLoadState state;
        if (heartbeat.empty()) {
            state = WorkspaceLoadState.NO_SNAPSHOT;
        } else if (heartbeat.stale()) {
            state = WorkspaceLoadState.STALE;
        } else if ("partial".equalsIgnoreCase(statsState)) {
            state = WorkspaceLoadState.PARTIAL;
        } else {
            state = WorkspaceLoadState.READY;
        }
        String detail = switch (state) {
            case NO_SNAPSHOT -> "Load FM26 with the save open to build the decision workspace.";
            case STALE -> "The save may have moved on. Refresh before acting on recommendations.";
            case PARTIAL -> "The snapshot is usable, but some season statistics are unknown.";
            case READY -> "Published snapshot is ready for squad decisions.";
            case LOADING -> "Reading the current FM26 snapshot…";
            case ERROR -> "The last refresh failed; the previous published snapshot is preserved.";
        };
        return new SnapshotStatusModel(state, playerCount, season, source, readAt, statsState,
                heartbeat.label(), detail);
    }

    public boolean usable() {
        return state == WorkspaceLoadState.READY || state == WorkspaceLoadState.STALE
                || state == WorkspaceLoadState.PARTIAL;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
