package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWorkspaceColumnsTest {
    @Test
    void exposesStableDefaultAndExpandedPlayerColumns() {
        assertEquals(27, PlayerWorkspaceColumns.all().size());
        assertEquals(9, PlayerWorkspaceColumns.visible(false).size());
        assertEquals(PlayerWorkspaceColumns.all().size(), PlayerWorkspaceColumns.visible(true).size());
        assertEquals(Set.of("NAME", "AGE", "CLUB", "POSITION", "CA", "PA",
                        "SALARY_WEEKLY_RAW", "ASKING_PRICE", "CONTRACT_END_DATE"),
                PlayerWorkspaceColumns.visible(false).stream()
                        .map(PlayerWorkspaceColumns.Column::key)
                        .collect(Collectors.toSet()));
    }

    @Test
    void marksMoneyAndNumericSortColumnsWithoutUiDependencies() {
        assertTrue(PlayerWorkspaceColumns.MONEY_COLUMNS.contains("ASKING_PRICE"));
        assertTrue(PlayerWorkspaceColumns.NUMERIC_SORT_COLUMNS.contains("CA"));
        assertTrue(!PlayerWorkspaceColumns.NUMERIC_SORT_COLUMNS.contains("NAME"));
    }
}
