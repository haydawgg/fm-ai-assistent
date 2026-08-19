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
        if (snapshot == null || snapshot.heartbeat() == null || snapshot.heartbeat().empty()) {
            return new DashboardViewState(Status.EMPTY, snapshot, "Load a live squad snapshot to populate the board.");
        }
        return new DashboardViewState(Status.SUCCESS, snapshot, "");
    }

    static DashboardViewState failure(Throwable error) {
        String message = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "Try refreshing the board."
                : error.getMessage();
        return new DashboardViewState(Status.FAILURE, null, message);
    }
}
