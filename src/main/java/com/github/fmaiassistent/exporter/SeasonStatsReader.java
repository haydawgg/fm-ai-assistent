package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.linux.GamePluginIdentity;

import java.io.IOException;
import java.time.LocalDate;

/** Build-aware boundary for current-season player statistics. */
@FunctionalInterface
public interface SeasonStatsReader {
    Result read(ProcessMemoryReader reader, int build, long playerRecord, LocalDate gameDate) throws IOException;

    default Result read(
            ProcessMemoryReader reader,
            int build,
            long playerRecord,
            LocalDate gameDate,
            GamePluginIdentity identity) throws IOException {
        return read(reader, build, playerRecord, gameDate);
    }

    /** Returns an explicit unavailable result until a layout is validated for a build. */
    static SeasonStatsReader unsupported() {
        return (reader, build, playerRecord, gameDate) -> Result.unavailable(
                "No validated season-statistics layout is registered for FM build " + build);
    }

    record Result(SeasonStats stats, State state, String message) {
        public Result {
            stats = stats == null ? SeasonStats.unknown() : stats;
            state = state == null ? State.UNAVAILABLE : state;
            message = message == null ? "" : message;
            if (!stats.isValid()) {
                stats = SeasonStats.unknown();
                state = State.UNAVAILABLE;
            }
        }

        public static Result available(SeasonStats stats) {
            return new Result(stats, State.AVAILABLE, "");
        }

        public static Result partial(SeasonStats stats, String message) {
            return new Result(stats, State.PARTIAL, message);
        }

        public static Result unavailable(String message) {
            return new Result(SeasonStats.unknown(), State.UNAVAILABLE, message);
        }

        public enum State {
            AVAILABLE,
            PARTIAL,
            UNAVAILABLE
        }
    }
}
