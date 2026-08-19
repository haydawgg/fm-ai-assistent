package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.mcp.SquadAdvice;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardSnapshotServiceTest {

    @Test
    void trimSummaryKeepsRecommendationsAndKnownValueHonest() {
        DashboardSnapshot.TrimSummary summary = DashboardSnapshotService.trim(List.of(
                sell("Sell me", "sell", 4_000_000L),
                sell("Loan me", "loan", null),
                sell("Keep me", "keep", 1_000_000L)));

        assertEquals(1, summary.sell());
        assertEquals(1, summary.loan());
        assertEquals(1, summary.keep());
        assertEquals(5_000_000L, summary.knownValue());
    }

    @Test
    void depthUsesNaturalPositionThresholdAndReportsAverage() {
        PlayerEntity keeper = player("Keeper", "Goalkeeper", 16);
        PlayerEntity backup = player("Backup", "Goalkeeper", 12);

        DashboardSnapshot.Depth row = DashboardSnapshotService.depth(List.of(keeper, backup)).stream()
                .filter(depth -> "GK".equals(depth.position()))
                .findFirst().orElseThrow();

        assertEquals(2, row.count());
        assertEquals(14, row.score());
    }

    private static SquadAdvice.SellRow sell(String name, String recommendation, Long askingPrice) {
        return new SquadAdvice.SellRow(1, name, 24, "ST", 100, 110, 5_000,
                askingPrice, "2030-06-30", 2, -5, recommendation, 40, List.of());
    }

    private static PlayerEntity player(String name, String position, int score) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("ca", 120);
        row.put(position, score);
        row.put("club", "Test FC");
        row.put("playing_club", "Test FC");
        return PlayerEntity.fromExportRow(row);
    }
}
