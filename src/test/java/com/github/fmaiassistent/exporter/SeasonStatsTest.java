package com.github.fmaiassistent.exporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonStatsTest {

    @Test
    void preservesValidZerosAndAllCoreValues() {
        SeasonStats stats = new SeasonStats(0, 0, 0, 0, 0, 0.0);

        assertTrue(stats.isValid());
        assertTrue(stats.isKnown());
        assertEquals(0, stats.appearances());
        assertEquals(0.0, stats.averageRating());
    }

    @Test
    void rejectsImpossibleCountersAndRatings() {
        assertFalse(new SeasonStats(4, 2, 3, 4, 0, 7.1).isValid());
        assertFalse(new SeasonStats(4, 2, 100, -1, 0, 7.1).isValid());
        assertFalse(new SeasonStats(4, 2, 100, 1, 0, 10.1).isValid());
    }

    @Test
    void missingReaderResultIsExplicitlyUnavailable() throws Exception {
        SeasonStatsReader.Result result = SeasonStatsReader.unsupported()
                .read(null, 0x123456, 0x1000, null);

        assertEquals(SeasonStatsReader.Result.State.UNAVAILABLE, result.state());
        assertFalse(result.stats().isKnown());
        assertNull(result.stats().goals());
    }

    @Test
    void invalidReaderResultFailsClosed() {
        SeasonStatsReader.Result result = new SeasonStatsReader.Result(
                new SeasonStats(1, 1, 2, 3, 0, 7.0),
                SeasonStatsReader.Result.State.AVAILABLE,
                "bad candidate");

        assertEquals(SeasonStatsReader.Result.State.UNAVAILABLE, result.state());
        assertFalse(result.stats().isKnown());
    }
}
