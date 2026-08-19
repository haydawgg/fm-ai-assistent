package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardSectionStateTest {
    @Test
    void distinguishesLoadingSuccessEmptyAndFailure() {
        assertEquals(DashboardSectionState.Status.LOADING, DashboardSectionState.loading().status());
        assertEquals(DashboardSectionState.Status.SUCCESS, DashboardSectionState.success("rows").status());
        assertEquals(DashboardSectionState.Status.EMPTY,
                DashboardSectionState.from("", String::isEmpty).status());
        assertEquals(DashboardSectionState.Status.FAILURE,
                DashboardSectionState.failure(new IllegalStateException("broken"), "fallback").status());
        assertNull(DashboardSectionState.failure(new IllegalStateException("broken"), "fallback").value());
    }
}
