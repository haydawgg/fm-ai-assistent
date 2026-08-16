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
    void bestXiSkipsInjuredNaturalAndLeavesAHole() {
        PlayerEntity fit = player("FitST", 120, 22, 5_000, "Striker", 16);
        PlayerEntity injured = player("HurtST", 160, 24, 5_000, "Striker", 18, true);
        List<SquadAdvice.XiPick> picks = SquadAdvice.bestXi(
                List.of(fit, injured),
                List.of(new SquadAdvice.XiSlot("ST", "", "")),
                (player, slot) -> null);
        assertFalse(picks.get(0).hole());
        assertEquals("FitST", picks.get(0).playerName());
    }

    @Test
    void bestXiLeavesHoleWhenOnlyNaturalIsInjured() {
        PlayerEntity injured = player("HurtGK", 150, 28, 5_000, "Goalkeeper", 18, true);
        List<SquadAdvice.XiPick> picks = SquadAdvice.bestXi(
                List.of(injured),
                List.of(new SquadAdvice.XiSlot("GK", "", "")),
                (player, slot) -> null);
        assertTrue(picks.get(0).hole());
        assertNull(picks.get(0).playerName());
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

    @Test
    void daysUntilExpiryUsesGameDate() {
        PlayerEntity player = player("Expiring", 140, 24, 8_000, "Striker", 18, false, "2026-06-30", "2026-01-01");
        assertEquals(180, SquadAdvice.daysUntilExpiry(player));
    }

    @Test
    void contractQueueMapsKeepToRenewAndSkipsFarContracts() {
        PlayerEntity renew = player("RenewMe", 150, 24, 8_000, "Striker", 18, false, "2026-03-01", "2026-01-01");
        PlayerEntity later = player("Later", 148, 24, 8_000, "Striker", 17, false, "2028-01-01", "2026-01-01");
        List<SquadAdvice.ContractRow> rows = SquadAdvice.contractQueue(List.of(renew, later), club());
        assertEquals(1, rows.size());
        assertEquals("RenewMe", rows.get(0).name());
        assertEquals("renew", rows.get(0).action());
        assertEquals(59, rows.get(0).daysUntilExpiry());
    }

    @Test
    void wageHealthComparesBillToPayroll() {
        ClubEntity club = club();
        List<PlayerEntity> squad = List.of(
                player("A", 140, 22, 8_000, "Striker", 18),
                player("B", 138, 24, 12_000, "Goalkeeper", 17));
        SquadAdvice.WageHealth health = SquadAdvice.wageHealth(squad, club);
        assertEquals(20_000, health.wageBillWeekly());
        assertEquals(100_000L, health.payrollBudget());
        assertEquals(0.2, health.usedFraction(), 0.0001);
        assertFalse(health.overBudget());
    }

    @Test
    void academyKeepsU21GkWhenMaxAgeAllows() {
        PlayerEntity gk = player("Gino", 68, 16, 193, "Goalkeeper", 18);
        PlayerEntity adult = player("Jachfe", 121, 28, 5_000, "Goalkeeper", 18);
        List<SquadAdvice.AcademyRow> rows = SquadAdvice.academy(List.of(gk, adult), 19);
        assertEquals(1, rows.size());
        assertEquals("Gino", rows.get(0).name());
        assertEquals("GK", rows.get(0).position());
    }

    @Test
    void academyKeepsU21AndComparesToFirstTeam() {
        PlayerEntity kid = player("Kid", 90, 18, 1_000, "Striker", 16);
        PlayerEntity starter = player("Starter", 140, 24, 8_000, "Striker", 18);
        List<SquadAdvice.AcademyRow> rows = SquadAdvice.academy(List.of(kid, starter), 21);
        assertEquals(1, rows.size());
        assertEquals("Kid", rows.get(0).name());
        assertEquals(90 - SquadAdvice.firstTeamAverageCa(List.of(kid, starter)), rows.get(0).vsFirstTeam());
        assertTrue(rows.get(0).dualPositions() >= 1);
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
        return player(name, ca, age, wage, position, positionScore, false, null, null);
    }

    private static PlayerEntity player(
            String name, int ca, int age, int wage, String position, int positionScore, boolean injured) {
        return player(name, ca, age, wage, position, positionScore, injured, null, null);
    }

    private static PlayerEntity player(
            String name,
            int ca,
            int age,
            int wage,
            String position,
            int positionScore,
            boolean injured,
            String contractEnd,
            String ageAsOf) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("ca", ca);
        row.put("pa", ca + 5);
        row.put("age", String.valueOf(age));
        row.put("salary_weekly_raw", wage);
        row.put("club", "Test FC");
        row.put("playing_club", "Test FC");
        row.put(position, positionScore);
        row.put("injured", injured);
        if (contractEnd != null) {
            row.put("contract_end_date", contractEnd);
        }
        if (ageAsOf != null) {
            row.put("age_as_of", ageAsOf);
        }
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
