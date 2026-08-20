package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.football.FirstXiPick;

import java.util.List;

/** Immutable read model for the Overview command center. */
record DashboardSnapshot(
        SnapshotHeartbeat.Status heartbeat,
        String clubName,
        boolean clubAvailable,
        boolean partial,
        Metrics metrics,
        List<Action> actions,
        Tactical tactical,
        List<FmAiAssistentTools.TransferShortlistRow> shortlist,
        List<Depth> depth,
        TrimSummary trim,
        boolean aiConfigured,
        SnapshotStatusModel status) {

    DashboardSnapshot(
            SnapshotHeartbeat.Status heartbeat,
            String clubName,
            boolean clubAvailable,
            boolean partial,
            Metrics metrics,
            List<Action> actions,
            Tactical tactical,
            List<FmAiAssistentTools.TransferShortlistRow> shortlist,
            List<Depth> depth,
            TrimSummary trim,
            boolean aiConfigured) {
        this(heartbeat, clubName, clubAvailable, partial, metrics, actions, tactical, shortlist, depth, trim,
                aiConfigured, SnapshotStatusModel.fromHeartbeat(heartbeat, metrics == null ? 0 : metrics.squadCount()));
    }

    record Metrics(
            int squadCount,
            Integer averageCa,
            Long transferBudget,
            Long squadValue,
            int knownValuations,
            long weeklyWages,
            Long payrollBudget,
            long injured,
            long expiring) {
    }

    record Action(String title, String detail, String count, String route, String tone) {
    }

    record Tactical(
            List<FirstXiPick> picks,
            List<String> unavailable,
            String formation,
            int holes,
            Integer firstXiStrength,
            Integer tacticalFit) {
    }

    record Depth(String position, int count, int score) {
    }

    record TrimSummary(int sell, int loan, int keep, Long knownValue) {
    }
}
