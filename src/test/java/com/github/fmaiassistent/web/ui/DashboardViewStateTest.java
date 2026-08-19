package com.github.fmaiassistent.web.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DashboardViewStateTest {
    @Test
    void distinguishesLoadingEmptySuccessAndFailure() {
        DashboardSnapshot empty = snapshot(new SnapshotHeartbeat.Status(true, false, "empty", "empty"));
        DashboardSnapshot ready = snapshot(new SnapshotHeartbeat.Status(false, false, "ready", "ready"));

        assertEquals(DashboardViewState.Status.LOADING, DashboardViewState.loading().status());
        assertEquals(DashboardViewState.Status.EMPTY, DashboardViewState.from(empty).status());
        assertEquals(DashboardViewState.Status.SUCCESS, DashboardViewState.from(ready).status());
        assertEquals(DashboardViewState.Status.FAILURE,
                DashboardViewState.failure(new IllegalStateException("read failed")).status());
        assertEquals("read failed", DashboardViewState.failure(new IllegalStateException("read failed")).message());
        assertSame(ready, DashboardViewState.from(ready).snapshot());
    }

    private static DashboardSnapshot snapshot(SnapshotHeartbeat.Status heartbeat) {
        return new DashboardSnapshot(
                heartbeat,
                "Ajax",
                true,
                false,
                new DashboardSnapshot.Metrics(0, null, null, null, 0, 0, null, 0, 0),
                List.of(),
                new DashboardSnapshot.Tactical(List.of(), List.of(), "", 0, null, null),
                List.of(),
                List.of(),
                new DashboardSnapshot.TrimSummary(0, 0, 0, null),
                false);
    }
}
