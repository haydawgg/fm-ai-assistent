package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadAdviceTest {

    @Test
    void ranksSurplusVeteranAsSell() {
        ClubEntity club = club();
        List<PlayerEntity> squad = List.of(
                player("Starter", 140, 22, 8_000, "DefenderLeft", 18),
                player("Backup", 138, 24, 8_000, "DefenderLeft", 17),
                player("Third", 136, 25, 8_000, "DefenderLeft", 16),
                player("Fourth", 90, 34, 40_000, "DefenderLeft", 15));
        List<SquadAdvice.SellRow> rows = SquadAdvice.sellShortlist(squad, club);
        SquadAdvice.SellRow fourth = rows.stream().filter(row -> "Fourth".equals(row.name())).findFirst().orElseThrow();
        assertEquals("sell", fourth.recommendation());
        assertTrue(fourth.sellScore() > 40);
    }

    @Test
    void compareSquadsReportsPositionGap() {
        List<PlayerEntity> left = List.of(player("LeftST", 150, 24, 10_000, "Striker", 18));
        List<PlayerEntity> right = List.of(player("RightST", 120, 24, 10_000, "Striker", 18));
        Map<String, Object> result = SquadAdvice.compareSquads("A", "B", left, right);
        @SuppressWarnings("unchecked")
        List<SquadAdvice.SquadCompareRow> positions = (List<SquadAdvice.SquadCompareRow>) result.get("positions");
        SquadAdvice.SquadCompareRow st = positions.stream().filter(row -> "ST".equals(row.position())).findFirst().orElseThrow();
        assertEquals(30, st.caGap());
        assertEquals("LeftST", st.leftName());
    }

    @Test
    void bestXiLeavesHoleWhenNoNatural() {
        List<PlayerEntity> squad = List.of(player("OnlyST", 140, 22, 5_000, "Striker", 18));
        List<SquadAdvice.XiPick> picks = SquadAdvice.bestXi(
                squad,
                List.of(new SquadAdvice.XiSlot("GK", "", ""), new SquadAdvice.XiSlot("ST", "", "")),
                (player, slot) -> null);
        assertTrue(picks.get(0).hole());
        assertFalse(picks.get(1).hole());
        assertEquals("OnlyST", picks.get(1).playerName());
    }

    @Test
    void bestXiFillsScarceSlotsFirstButKeepsFormationOrder() {
        List<PlayerEntity> squad = List.of(hybrid("KeeperForward", 140, 22, 5_000, 18, 18));
        List<SquadAdvice.XiPick> picks = SquadAdvice.bestXi(
                squad,
                List.of(new SquadAdvice.XiSlot("ST", "", ""), new SquadAdvice.XiSlot("GK", "", "")),
                (player, slot) -> null);
        assertEquals("ST", picks.get(0).position());
        assertEquals("GK", picks.get(1).position());
        assertTrue(picks.get(0).hole());
        assertFalse(picks.get(1).hole());
        assertEquals("KeeperForward", picks.get(1).playerName());
    }

    @Test
    void parseTacticSlotsSkipsHeader() {
        List<SquadAdvice.XiSlot> slots = FmAiAssistentTools.parseTacticSlots("""
                Player,in possesion role,out of possesion role
                GK,Ball playing GK,Sweeper keeper
                ST,Advanced Forward,Pressing Forward
                """);
        assertEquals(2, slots.size());
        assertEquals("GK", slots.get(0).position());
        assertEquals("ST", slots.get(1).position());
    }

    @Test
    void parseTacticSlotsMapsFormationSideCodes() {
        List<SquadAdvice.XiSlot> slots = FmAiAssistentTools.parseTacticSlots("""
                DCR,,
                MCL,,
                STCR,,
                """);
        assertEquals(List.of("DC", "MC", "ST"), slots.stream().map(SquadAdvice.XiSlot::position).toList());
    }

    @Test
    void asNumberAcceptsNumbersOnly() {
        assertEquals(12L, SquadAdvice.asNumber(12));
        assertEquals(12L, SquadAdvice.asNumber(12L));
        assertNull(SquadAdvice.asNumber("N/A"));
        assertNull(SquadAdvice.asNumber("true"));
        assertNull(SquadAdvice.asNumber(null));
    }

    private static ClubEntity club() {
        Map<String, Object> row = new HashMap<>();
        row.put("sourceAddress", 1L);
        row.put("name", "Test FC");
        row.put("gender", "male");
        row.put("competition", "Test League");
        row.put("reputation", 5000);
        row.put("nation", "Netherlands");
        row.put("balance", 10_000_000L);
        row.put("transferBudget", 5_000_000L);
        row.put("payrollBudget", 100_000L);
        return ClubEntity.fromExportRow(row);
    }

    private static PlayerEntity player(String name, int ca, int age, int wage, String position, int positionScore) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("ca", ca);
        row.put("pa", ca + 5);
        row.put("age", String.valueOf(age));
        row.put("salary_weekly_raw", wage);
        row.put("club", "Test FC");
        row.put("playing_club", "Test FC");
        row.put(position, positionScore);
        return PlayerEntity.fromExportRow(row);
    }

    private static PlayerEntity hybrid(String name, int ca, int age, int wage, int goalkeeper, int striker) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("ca", ca);
        row.put("pa", ca + 5);
        row.put("age", String.valueOf(age));
        row.put("salary_weekly_raw", wage);
        row.put("club", "Test FC");
        row.put("playing_club", "Test FC");
        row.put("Goalkeeper", goalkeeper);
        row.put("Striker", striker);
        return PlayerEntity.fromExportRow(row);
    }
}
