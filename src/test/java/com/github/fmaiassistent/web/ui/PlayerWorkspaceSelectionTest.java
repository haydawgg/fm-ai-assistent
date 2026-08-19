package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWorkspaceSelectionTest {
    @Test
    void selectingAnotherPlayerCompletesComparison() {
        PlayerEntity first = player(1L, "One");
        PlayerEntity second = player(2L, "Two");
        PlayerWorkspaceSelection selection = new PlayerWorkspaceSelection();

        selection.select(first);
        selection.startCompare();
        selection.select(second);

        assertSame(first, selection.compareAnchor());
        assertSame(second, selection.selected());
        assertFalse(selection.awaitingCompare());
    }

    @Test
    void reconciliationClearsSelectionsThatLeftTheFilteredRows() {
        PlayerEntity first = player(1L, "One");
        PlayerWorkspaceSelection selection = new PlayerWorkspaceSelection();
        selection.select(first);
        selection.startCompare();

        selection.reconcile(List.of());

        assertTrue(selection.selected() == null);
        assertTrue(selection.compareAnchor() == null);
        assertFalse(selection.awaitingCompare());
    }

    private static PlayerEntity player(Long id, String name) {
        return PlayerEntity.fromExportRow(Map.of("NAME", name));
    }
}
