package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerWorkspaceLoaderTest {
    @Test
    void resultDistinguishesEmptyAndFilteredRows() {
        PlayerWorkspaceLoader.Result empty = new PlayerWorkspaceLoader.Result(List.of(), 4);
        assertTrue(empty.empty());
        assertTrue(empty.filtered());

        PlayerWorkspaceLoader.Result complete = new PlayerWorkspaceLoader.Result(
                List.of(PlayerEntity.fromExportRow(java.util.Map.of())), 1);
        assertFalse(complete.empty());
        assertFalse(complete.filtered());
    }

    @Test
    void resultDefensivelyCopiesRowsAndNormalizesInvalidTotals() {
        PlayerWorkspaceLoader.Result result = new PlayerWorkspaceLoader.Result(null, -1);
        assertEquals(List.of(), result.rows());
        assertEquals(0, result.totalCount());
    }
}
