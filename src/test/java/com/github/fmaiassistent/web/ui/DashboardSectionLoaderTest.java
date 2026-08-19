package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardSectionLoaderTest {
    @Test
    void turnsIndependentQueryFailuresIntoFailureState() {
        DashboardSectionState<List<String>> state = DashboardSectionLoader.load(
                () -> { throw new IllegalStateException("section unavailable"); },
                List::isEmpty,
                "fallback");

        assertEquals(DashboardSectionState.Status.FAILURE, state.status());
        assertEquals("section unavailable", state.message());
        assertEquals(List.of(), DashboardSectionLoader.or(state, List.of()));
    }

    @Test
    void classifiesEmptySectionWithoutThrowing() {
        DashboardSectionState<List<String>> state = DashboardSectionLoader.load(
                List::of,
                List::isEmpty,
                "fallback");

        assertEquals(DashboardSectionState.Status.EMPTY, state.status());
    }
}
