package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffEntriesTest {

    /** Staff/retired people in the People export have no position attributes (max position score 1). */
    @Test
    void staffEntryWithoutPositionsIsNotPlayable() {
        PlayerEntity staff = PlayerEntity.fromExportRow(Map.<String, Object>of(
                "name", "Rhys Garcia",
                "ca", 166,
                "pa", 70,
                "age", 45,
                "club", "World",
                "nationality", "World",
                "salary_weekly_raw", 0,
                "asking_price", 0));
        assertFalse(MarketValuation.hasPlayablePosition(staff));
    }

    @Test
    void realPlayerWithDefinedPositionIsPlayable() {
        PlayerEntity player = PlayerEntity.fromExportRow(Map.<String, Object>of(
                "name", "Edson Trinidad",
                "ca", 150,
                "pa", 158,
                "age", 20,
                "club", "Slovan Bratislava",
                "nationality", "Peru",
                "Striker", 16,
                "MidfielderCentral", 14));
        assertTrue(MarketValuation.hasPlayablePosition(player));
    }

    @Test
    void freeAgentWithPositionsStillCountsAsPlayer() {
        PlayerEntity freeAgent = PlayerEntity.fromExportRow(Map.<String, Object>of(
                "name", "Free Agent GK",
                "ca", 120,
                "pa", 130,
                "age", 25,
                "club", "",
                "Goalkeeper", 18));
        assertTrue(MarketValuation.hasPlayablePosition(freeAgent));
    }

    @Test
    void thresholdBoundary() {
        PlayerEntity below = PlayerEntity.fromExportRow(Map.<String, Object>of("name", "low", "Striker", 4));
        PlayerEntity atFloor = PlayerEntity.fromExportRow(Map.<String, Object>of("name", "floor", "Striker", 5));
        assertFalse(MarketValuation.hasPlayablePosition(below));
        assertTrue(MarketValuation.hasPlayablePosition(atFloor));
    }
}
