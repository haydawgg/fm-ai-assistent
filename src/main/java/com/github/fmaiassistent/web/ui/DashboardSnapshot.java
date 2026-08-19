package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.SquadAdvice;

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
        boolean aiConfigured) {

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
            List<SquadAdvice.XiPick> picks,
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
