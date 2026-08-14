package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionsTest {

    @Test
    void scoreReturnsZeroForBlankPosition() {
        PlayerEntity player = centreBack(18);
        assertEquals(0, Positions.score(player, null));
        assertEquals(0, Positions.score(player, " "));
    }

    @Test
    void scoreAcceptsSideCodesThatCanonicalCodeMaps() {
        PlayerEntity player = centreBack(17);
        assertDoesNotThrow(() -> Positions.score(player, "DCR"));
        assertEquals(17, Positions.score(player, "DCR"));
        assertEquals(17, Positions.score(player, "DC"));
    }

    @Test
    void scoreReturnsZeroForUnknownCode() {
        assertEquals(0, Positions.score(centreBack(18), "XYZ"));
    }

    @Test
    void canonicalSideCodesStillScoreNaturalCentreBacks() {
        assertTrue(Positions.score(centreBack(18), "DCL") >= 15);
    }

    private static PlayerEntity centreBack(int score) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "CB");
        row.put("DefenderCentral", score);
        return PlayerEntity.fromExportRow(row);
    }
}
