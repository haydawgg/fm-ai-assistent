package com.github.fmaiassistent.web.ui;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared adapter for independent dashboard reads.
 *
 * <p>Section-specific code supplies only its query and empty rule. Failures
 * are converted into the same explicit lifecycle state used by the view,
 * while callers can still choose a safe fallback for the aggregate snapshot.</p>
 */
final class DashboardSectionLoader {
    private DashboardSectionLoader() {
    }

    static <T> DashboardSectionState<T> load(
            Supplier<T> query,
            Predicate<T> empty,
            String failureMessage) {
        try {
            return DashboardSectionState.from(query.get(), empty);
        } catch (RuntimeException error) {
            return DashboardSectionState.failure(error, failureMessage);
        }
    }

    static <T> T or(DashboardSectionState<T> state, T fallback) {
        return state.value() == null ? fallback : state.value();
    }
}
