package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerWorkspaceFormattingTest {
    @Test
    void formatsPlainAndMoneyColumnsConsistently() {
        assertEquals("", PlayerWorkspaceFormatting.display(null));
        assertEquals("Ajax", PlayerWorkspaceFormatting.display("Ajax"));
        assertEquals("£1,000,000", PlayerWorkspaceFormatting.column("ASKING_PRICE", 1_000_000, MoneyCurrency.POUND));
        assertEquals(MoneyDisplay.format(1_000_000, MoneyCurrency.DOLLAR),
                PlayerWorkspaceFormatting.column("SALARY_WEEKLY_RAW", 1_000_000, MoneyCurrency.DOLLAR));
    }

    @Test
    void parsesSortableValuesAndOrdersNullsLast() {
        assertEquals(42L, PlayerWorkspaceFormatting.sortableLong("42"));
        assertEquals(null, PlayerWorkspaceFormatting.sortableLong("not-a-number"));
        assertEquals(-1, PlayerWorkspaceFormatting.compareLongs(1L, 2L));
        assertEquals(1, PlayerWorkspaceFormatting.compareLongs(null, 2L));
        assertEquals(0, PlayerWorkspaceFormatting.compareLongs(null, null));
    }
}
