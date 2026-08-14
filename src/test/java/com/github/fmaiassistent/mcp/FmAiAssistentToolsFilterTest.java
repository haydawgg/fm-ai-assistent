package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FmAiAssistentToolsFilterTest {

    @Test
    void inRangeDoesNotDropRowsWhenNoBoundsAreSet() {
        assertTrue(FmAiAssistentTools.inRange(null, null, null));
        assertTrue(FmAiAssistentTools.inRange(140, null, null));
        assertFalse(FmAiAssistentTools.inRange(null, 100, null));
        assertTrue(FmAiAssistentTools.inRange(140, 100, 150));
        assertFalse(FmAiAssistentTools.inRange(90, 100, 150));
    }

    @Test
    void unknownAskingPriceIsNotTreatedAsFree() {
        assertTrue(FmAiAssistentTools.askingPriceWithinMax(null, "Ajax", null));
        assertFalse(FmAiAssistentTools.askingPriceWithinMax(null, "Ajax", 5_000_000L));
        assertFalse(FmAiAssistentTools.askingPriceWithinMax(0L, "Ajax", 5_000_000L));
        assertTrue(FmAiAssistentTools.askingPriceWithinMax(null, "", 5_000_000L));
        assertTrue(FmAiAssistentTools.askingPriceWithinMax(1_000_000L, "Ajax", 5_000_000L));
        assertFalse(FmAiAssistentTools.askingPriceWithinMax(9_000_000L, "Ajax", 5_000_000L));
    }

    @Test
    void unknownWageFailsAMaximumSalaryFilter() {
        assertTrue(FmAiAssistentTools.salaryWithinMax(null, null));
        assertFalse(FmAiAssistentTools.salaryWithinMax(null, 10_000));
        assertTrue(FmAiAssistentTools.salaryWithinMax(8_000, 10_000));
        assertFalse(FmAiAssistentTools.salaryWithinMax(12_000, 10_000));
    }

    @Test
    void currentSquadUsesPlayingClubAndExcludesLoanedOutPlayers() {
        PlayerEntity atParent = player("Home", "Feyenoord", "Feyenoord");
        PlayerEntity loanedOut = player("Away", "Feyenoord", "Excelsior");
        PlayerEntity loanedIn = player("In", "Ajax", "Feyenoord");
        List<PlayerEntity> players = List.of(atParent, loanedOut, loanedIn);

        List<String> feyenoord = FmAiAssistentTools.currentSquad(players, "Feyenoord").stream()
                .map(PlayerEntity::getName)
                .toList();
        assertEquals(List.of("Home", "In"), feyenoord);
    }

    @Test
    void ownedSquadExcludesLoanedInPlayers() {
        PlayerEntity atParent = player("Home", "Feyenoord", "Feyenoord");
        PlayerEntity loanedIn = player("In", "Ajax", "Feyenoord");
        List<PlayerEntity> squad = FmAiAssistentTools.currentSquad(List.of(atParent, loanedIn), "Feyenoord");

        List<String> owned = FmAiAssistentTools.ownedSquad(squad, "Feyenoord").stream()
                .map(PlayerEntity::getName)
                .toList();
        assertEquals(List.of("Home"), owned);
    }

    @Test
    void resolvePriceCapHonoursExplicitMaxAboveBudget() {
        assertEquals(5_000_000L, FmAiAssistentTools.resolvePriceCap(null, 5_000_000L));
        assertEquals(12_000_000L, FmAiAssistentTools.resolvePriceCap(12_000_000L, 5_000_000L));
        assertEquals(1_000_000L, FmAiAssistentTools.resolvePriceCap(1_000_000L, 5_000_000L));
    }

    @Test
    void recruitmentWageCapRejectsUnknownWages() {
        assertFalse(FmAiAssistentTools.wageFits(null, 10_000));
        assertTrue(FmAiAssistentTools.wageFits(8_000, 10_000));
        assertFalse(FmAiAssistentTools.salaryWithinMax(null, 10_000));
    }

    @Test
    void roleNamesMatchWithHyphensAndGkAlias() {
        assertTrue(FmAiAssistentTools.roleKeysEqual("Ball-Playing Goalkeeper", "Ball Playing GK"));
        assertTrue(FmAiAssistentTools.rolesMatch("Ball-Playing Goalkeeper", "Ball Playing GK"));
        assertTrue(FmAiAssistentTools.rolesMatch("Deep-Lying Playmaker", "Deep Lying Playmaker"));
    }

    @Test
    void pickPlayerThrowsWhenContainsMatchIsAmbiguous() {
        PlayerEntity david = named("David Silva", 160);
        PlayerEntity bernardo = named("Bernardo Silva", 150);
        assertEquals("David Silva", FmAiAssistentTools.pickPlayer(List.of(david, bernardo), "Silva", false).getName());
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> FmAiAssistentTools.pickPlayer(List.of(david, bernardo), "Silva", true));
        assertTrue(error.getMessage().contains("ambiguous"));
    }

    @Test
    void samePlayerDetectsIdenticalResolvedPlayers() {
        PlayerEntity one = player("Home", "Feyenoord", "Feyenoord");
        PlayerEntity clone = player("Home", "Feyenoord", "Feyenoord");
        assertTrue(FmAiAssistentTools.samePlayer(one, one));
        assertTrue(FmAiAssistentTools.samePlayer(one, clone));
        assertTrue(!FmAiAssistentTools.samePlayer(one, player("Away", "Ajax", "Ajax")));
    }

    private static PlayerEntity named(String name, int ca) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("club", "Man City");
        row.put("playing_club", "Man City");
        row.put("ca", ca);
        row.put("Striker", 15);
        return PlayerEntity.fromExportRow(row);
    }

    private static PlayerEntity player(String name, String club, String playingClub) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("club", club);
        row.put("playing_club", playingClub);
        row.put("Striker", 15);
        return PlayerEntity.fromExportRow(row);
    }
}
