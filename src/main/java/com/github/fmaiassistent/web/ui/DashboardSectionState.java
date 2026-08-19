package com.github.fmaiassistent.web.ui;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shared lifecycle model for dashboard sections.
 *
 * <p>A section never uses {@code null} to communicate why it has no data:
 * loading, empty, and failure are explicit states. The model is deliberately
 * UI-framework-free so section loaders and view tests can share it.</p>
 */
record DashboardSectionState<T>(Status status, T value, String message) {
    enum Status {
        LOADING,
        SUCCESS,
        EMPTY,
        FAILURE
    }

    DashboardSectionState {
        status = Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
    }

    static <T> DashboardSectionState<T> loading() {
        return new DashboardSectionState<>(Status.LOADING, null, "");
    }

    static <T> DashboardSectionState<T> success(T value) {
        return new DashboardSectionState<>(Status.SUCCESS, value, "");
    }

    static <T> DashboardSectionState<T> from(T value, Predicate<T> empty) {
        return empty != null && empty.test(value)
                ? new DashboardSectionState<>(Status.EMPTY, value, "No data is available for this section.")
                : success(value);
    }

    static <T> DashboardSectionState<T> failure(Throwable error, String fallback) {
        String message = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? fallback
                : error.getMessage();
        return new DashboardSectionState<>(Status.FAILURE, null, message);
    }
}
