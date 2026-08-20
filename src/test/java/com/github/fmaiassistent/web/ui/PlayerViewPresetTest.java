package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerViewPresetTest {
    @Test
    void focusedPresetsExposeTheFieldsNeededForTheirDecision() {
        var performance = PlayerWorkspaceColumns.visible(PlayerViewPreset.PERFORMANCE)
                .stream().map(PlayerWorkspaceColumns.Column::key).collect(Collectors.toSet());
        var contracts = PlayerWorkspaceColumns.visible(PlayerViewPreset.CONTRACTS)
                .stream().map(PlayerWorkspaceColumns.Column::key).collect(Collectors.toSet());

        assertTrue(performance.containsAll(java.util.Set.of("APPEARANCES", "STARTS", "MINUTES", "GOALS", "ASSISTS", "AVERAGE_RATING")));
        assertTrue(contracts.containsAll(java.util.Set.of("SALARY_WEEKLY_RAW", "CONTRACT_END_DATE", "TRANSFER_AGREED")));
    }

    @Test
    void fullDataPresetRetainsEveryColumn() {
        assertTrue(PlayerWorkspaceColumns.visible(PlayerViewPreset.FULL_DATA).size()
                >= PlayerWorkspaceColumns.visible(false).size());
    }
}
