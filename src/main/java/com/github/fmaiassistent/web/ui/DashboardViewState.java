package com.github.fmaiassistent.web.ui;

/**
 * Lifecycle state for the dashboard composition seam. The view can render
 * every state without guessing whether a missing snapshot means loading,
 * empty data, or a failed read.
 */
record DashboardViewState(Status status, DashboardSnapshot snapshot, String message) {
    enum Status {
        LOADING,
        SUCCESS,
        EMPTY,
        FAILURE
    }

    static DashboardViewState loading() {
        return new DashboardViewState(Status.LOADING, null, "");
    }

    static DashboardViewState from(DashboardSnapshot snapshot) {
        DashboardSectionState<DashboardSnapshot> state = DashboardSectionState.from(
                snapshot,
                value -> value == null || value.heartbeat() == null || value.heartbeat().empty());
        if (state.status() == DashboardSectionState.Status.EMPTY) {
            return new DashboardViewState(Status.EMPTY, snapshot, "Load a live squad snapshot to populate the board.");
        }
        return new DashboardViewState(Status.SUCCESS, snapshot, state.message());
    }

    static DashboardViewState failure(Throwable error) {
        DashboardSectionState<DashboardSnapshot> state = DashboardSectionState.failure(error, "Try refreshing the board.");
        return new DashboardViewState(Status.FAILURE, null, state.message());
    }
}
