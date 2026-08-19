package com.github.fmaiassistent.football;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAnalysisRulesTest {
    @Test
    void unknownPricesAreNotTreatedAsFreePlayers() {
        assertFalse(PlayerAnalysisRules.askingPriceWithinMax(null, "Ajax", 5_000_000L));
        assertTrue(PlayerAnalysisRules.askingPriceWithinMax(null, "", 5_000_000L));
        assertTrue(PlayerAnalysisRules.askingPriceWithinMax(1_000_000L, "Ajax", 5_000_000L));
    }

    @Test
    void roleMatchingAndClubFamiliesAreTransportIndependent() {
        assertTrue(PlayerAnalysisRules.rolesMatch("Ball-Playing Goalkeeper", "Ball Playing GK"));
        assertEquals("kvc westerlo", PlayerAnalysisRules.clubFamilyStem("KVC Westerlo U21"));
        assertTrue(PlayerAnalysisRules.inClubFamily("KVC Westerlo B", "KVC Westerlo"));
        assertFalse(PlayerAnalysisRules.inClubFamily("Ajax Cape Town", "Ajax"));
    }

    @Test
    void missingAgeAndJoinDateRemainUnknown() {
        PlayerEntity player = PlayerEntity.fromExportRow(java.util.Map.of());
        assertNull(PlayerAnalysisRules.effectiveAge(player));
        assertFalse(PlayerAnalysisRules.recentlyJoinedCurrentClub(player, Period.ofDays(1)));
    }
}
