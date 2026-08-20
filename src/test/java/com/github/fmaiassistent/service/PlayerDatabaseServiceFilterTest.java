package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDatabaseServiceFilterTest {

    @Test
    void unknownWageIsKeptWhenWageIsNotFiltered() {
        PlayerEntity player = player("Hero", 140, null);
        PlayerFilterCriteria filter = new PlayerFilterCriteria(
                "Hero", "", "", "", "", null, null, null, null, "",
                null, null, null, null, null, null,
                100, null, null, null, null, null, null, null, null,
                Map.of(), Map.of());
        assertTrue(PlayerDatabaseService.matchesPlayerFilter(player, filter));
    }

    @Test
    void unknownWageFailsAMaximumWageFilter() {
        PlayerEntity player = player("Hero", 140, null);
        PlayerFilterCriteria filter = new PlayerFilterCriteria(
                "Hero", "", "", "", "", null, null, null, null, "",
                null, null, null, null, null, null,
                100, null, null, null, null, null, null, null, 10_000L,
                Map.of(), Map.of());
        assertFalse(PlayerDatabaseService.matchesPlayerFilter(player, filter));
    }

    @Test
    void blankStoredAgeStillMatchesU21WhenDobIsKnown() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Kid");
        row.put("ca", 80);
        row.put("Striker", 15);
        row.put("age", "");
        row.put("date_of_birth", "2010-02-01");
        row.put("age_as_of", "2026-08-16");
        PlayerEntity player = PlayerEntity.fromExportRow(row);
        PlayerFilterCriteria filter = new PlayerFilterCriteria(
                "Kid", "", "", "", "", null, 21, null, null, "",
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                Map.of(), Map.of());
        assertTrue(PlayerDatabaseService.matchesPlayerFilter(player, filter));
    }

    @Test
    void unknownSeasonStatsFailPerformanceMinimums() {
        PlayerEntity player = player("Unknown", 140, 10_000);
        PlayerFilterCriteria.Advanced advanced = new PlayerFilterCriteria.Advanced(
                null, null, null, null, null,
                PlayerFilterCriteria.LoanStatus.ANY,
                PlayerFilterCriteria.ClubScope.EITHER,
                1, null, null, null, null, null,
                null, null, null, null, null, null);
        assertFalse(PlayerDatabaseService.matchesPlayerFilter(player,
                PlayerFilterCriteria.empty().withAdvanced(advanced)));
    }

    @Test
    void realZeroSeasonStatsMatchMinimumZero() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Zero");
        row.put("ca", 140);
        row.put("Striker", 15);
        row.put("appearances", 0);
        row.put("starts", 0);
        row.put("minutes", 0);
        row.put("goals", 0);
        row.put("assists", 0);
        row.put("average_rating", 0.0);
        PlayerEntity player = PlayerEntity.fromExportRow(row);
        PlayerFilterCriteria.Advanced advanced = new PlayerFilterCriteria.Advanced(
                null, null, null, null, null,
                PlayerFilterCriteria.LoanStatus.ANY,
                PlayerFilterCriteria.ClubScope.EITHER,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0);

        assertTrue(PlayerDatabaseService.matchesPlayerFilter(player,
                PlayerFilterCriteria.empty().withAdvanced(advanced)));
    }

    private static PlayerEntity player(String name, int ca, Integer wage) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("ca", ca);
        row.put("Striker", 15);
        if (wage != null) {
            row.put("salary_weekly_raw", wage);
        }
        return PlayerEntity.fromExportRow(row);
    }
}
