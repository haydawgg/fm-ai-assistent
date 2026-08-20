package com.github.fmaiassistent.exporter;

import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.github.fmaiassistent.linux.GamePluginIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * Decoder for a validated FM build-specific season-statistics block.
 *
 * <p>No offsets are shipped by default: an unverified layout must never turn
 * arbitrary memory into player statistics. A supported build can be enabled
 * by supplying a layout only after it has been validated against identity,
 * season date, and plausible ranges.</p>
 */
public final class BuildSeasonStatsReader implements SeasonStatsReader {
    private static final int MAX_APPEARANCES = 1_000;
    private static final int MAX_MINUTES = 100_000;
    private static final int MAX_GOALS = 10_000;
    private static final int MAX_ASSISTS = 10_000;

    private final Map<Integer, Layout> layouts;
    private final Map<ProfileKey, Layout> profileLayouts;

    public BuildSeasonStatsReader() {
        this(Map.of());
    }

    public BuildSeasonStatsReader(Map<Integer, Layout> layouts) {
        this(layouts, Map.of());
    }

    public BuildSeasonStatsReader(Map<Integer, Layout> layouts, Map<ProfileKey, Layout> profileLayouts) {
        this.layouts = layouts == null ? Map.of() : Map.copyOf(layouts);
        this.profileLayouts = profileLayouts == null ? Map.of() : Map.copyOf(profileLayouts);
    }

    @Override
    public Result read(ProcessMemoryReader reader, int build, long playerRecord, LocalDate gameDate)
            throws IOException {
        return read(reader, build, playerRecord, gameDate, GamePluginIdentity.unknown());
    }

    @Override
    public Result read(
            ProcessMemoryReader reader,
            int build,
            long playerRecord,
            LocalDate gameDate,
            GamePluginIdentity identity) throws IOException {
        Layout layout = profileLayouts.get(new ProfileKey(build, identity == null ? "" : identity.sha256()));
        if (layout == null) {
            layout = layouts.get(build);
        }
        if (layout == null) {
            return Result.unavailable("No validated season-statistics layout is registered for FM build " + build);
        }
        if (reader == null || playerRecord <= 0 || gameDate == null) {
            return Result.unavailable("Player identity or current season date is unavailable");
        }

        long block = reader.qwordOrNull(playerRecord + layout.statsPointerOffset()).orElse(0L);
        if (block == 0L) {
            return Result.unavailable("Season-statistics pointer is unavailable");
        }
        if (reader.readU64(block + layout.playerRecordOffset()) != playerRecord) {
            return Result.unavailable("Season-statistics identity does not match the player record");
        }
        int seasonStartYear = reader.readI32(block + layout.seasonStartYearOffset());
        int seasonStartMonth = reader.readI32(block + layout.seasonStartMonthOffset());
        if (seasonStartMonth < 1 || seasonStartMonth > 12
                || !sameSeason(gameDate, seasonStartYear, seasonStartMonth)) {
            return Result.unavailable("Season-statistics block is not for the current season");
        }

        Integer appearances = readInt(reader, block + layout.appearancesOffset());
        Integer starts = readInt(reader, block + layout.startsOffset());
        Integer minutes = readInt(reader, block + layout.minutesOffset());
        Integer goals = readInt(reader, block + layout.goalsOffset());
        Integer assists = readInt(reader, block + layout.assistsOffset());
        Integer ratingRaw = readInt(reader, block + layout.averageRatingOffset());
        Double averageRating = ratingRaw == null ? null : ratingRaw / layout.averageRatingScale();
        SeasonStats stats = new SeasonStats(appearances, starts, minutes, goals, assists, averageRating);
        if (!plausible(stats)) {
            return Result.unavailable("Season-statistics values failed plausibility checks");
        }
        if (!stats.isKnown()) {
            return Result.unavailable("Season-statistics values are unreadable");
        }
        boolean partial = appearances == null || starts == null || minutes == null
                || goals == null || assists == null || averageRating == null;
        return partial
                ? Result.partial(stats, "One or more season-statistics fields were unreadable")
                : Result.available(stats);
    }

    private static Integer readInt(ProcessMemoryReader reader, long address) {
        try {
            return reader.readI32(address);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static boolean sameSeason(LocalDate gameDate, int startYear, int startMonth) {
        int expectedStartYear = gameDate.getMonthValue() >= 7 ? gameDate.getYear() : gameDate.getYear() - 1;
        return startYear == expectedStartYear && startMonth == 7;
    }

    private static boolean plausible(SeasonStats stats) {
        return within(stats.appearances(), 0, MAX_APPEARANCES)
                && within(stats.starts(), 0, MAX_APPEARANCES)
                && within(stats.minutes(), 0, MAX_MINUTES)
                && within(stats.goals(), 0, MAX_GOALS)
                && within(stats.assists(), 0, MAX_ASSISTS)
                && stats.isValid();
    }

    private static boolean within(Integer value, int min, int max) {
        return value == null || (value >= min && value <= max);
    }

    /** Offsets are relative to the validated statistics block, except the pointer offset. */
    public record Layout(
            int statsPointerOffset,
            int playerRecordOffset,
            int seasonStartYearOffset,
            int seasonStartMonthOffset,
            int appearancesOffset,
            int startsOffset,
            int minutesOffset,
            int goalsOffset,
            int assistsOffset,
            int averageRatingOffset,
            double averageRatingScale) {
        public Layout {
            if (averageRatingScale <= 0.0 || !Double.isFinite(averageRatingScale)) {
                throw new IllegalArgumentException("averageRatingScale must be positive and finite");
            }
        }
    }

    /** A layout may be enabled for one exact native module hash. */
    public record ProfileKey(int build, String sha256) {
        public ProfileKey {
            sha256 = sha256 == null ? "" : sha256.toLowerCase(java.util.Locale.ROOT);
        }
    }
}
