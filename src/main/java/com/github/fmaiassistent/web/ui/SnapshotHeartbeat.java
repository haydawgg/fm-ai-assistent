package com.github.fmaiassistent.web.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

final class SnapshotHeartbeat {
    static final Duration STALE_AFTER = Duration.ofHours(3);

    private SnapshotHeartbeat() {
    }

    record Status(boolean empty, boolean stale, String label, String title) {
    }

    static Status from(Map<String, Object> metadata, long playerCount) {
        return from(metadata, playerCount, Clock.systemDefaultZone());
    }

    static Status from(Map<String, Object> metadata, long playerCount, Clock clock) {
        if (playerCount <= 0) {
            return new Status(true, false, "No snapshot — Load with FM26 running", "Load from the top bar with FM26 running");
        }
        Object gameDate = metadata == null ? null : metadata.get("game_date");
        Object loadedAt = metadata == null ? null : metadata.get("loaded_at");
        String date = text(gameDate).isBlank() ? "date unknown" : text(gameDate);
        OffsetDateTime loaded = parse(loadedAt);
        if (loaded == null) {
            return new Status(false, false,
                    playerCount + " players · " + date,
                    "Last load time unknown");
        }
        Duration age = Duration.between(loaded, OffsetDateTime.now(clock));
        if (age.isNegative()) {
            age = Duration.ZERO;
        }
        boolean stale = age.compareTo(STALE_AFTER) > 0;
        String relative = relative(age);
        String label = stale
                ? playerCount + " players · " + date + " · FM has moved on — reload"
                : playerCount + " players · " + date + " · " + relative;
        return new Status(false, stale, label, "Loaded " + loaded + " (" + relative + ")");
    }

    static String relative(Duration age) {
        long minutes = Math.max(0, age.toMinutes());
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }

    private static OffsetDateTime parse(Object value) {
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
