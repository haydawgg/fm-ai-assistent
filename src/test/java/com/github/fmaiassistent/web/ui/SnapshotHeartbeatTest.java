package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotHeartbeatTest {

    @Test
    void emptyWhenNoPlayers() {
        SnapshotHeartbeat.Status status = SnapshotHeartbeat.from(Map.of(), 0);
        assertTrue(status.empty());
        assertFalse(status.stale());
        assertTrue(status.label().contains("No snapshot"));
    }

    @Test
    void staleWhenLoadIsOlderThanThreeHours() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T18:00:00Z"), ZoneOffset.UTC);
        Map<String, Object> meta = Map.of(
                "game_date", "2026-01-15",
                "loaded_at", OffsetDateTime.parse("2026-08-14T12:00:00Z").toString());
        SnapshotHeartbeat.Status status = SnapshotHeartbeat.from(meta, 1200, clock);
        assertFalse(status.empty());
        assertTrue(status.stale());
        assertTrue(status.label().contains("FM has moved on"));
    }

    @Test
    void relativeMinutes() {
        assertEquals("12m ago", SnapshotHeartbeat.relative(Duration.ofMinutes(12)));
        assertEquals("just now", SnapshotHeartbeat.relative(Duration.ofSeconds(20)));
    }
}
