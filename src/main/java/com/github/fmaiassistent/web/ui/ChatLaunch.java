package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.QueryParameters;

import java.util.Map;

final class ChatLaunch {
    private ChatLaunch() {
    }

    static void open(String prompt) {
        UI ui = UI.getCurrent();
        if (ui == null || prompt == null || prompt.isBlank()) {
            return;
        }
        ui.navigate("chat", QueryParameters.simple(Map.of("q", prompt.strip())));
    }

    static String argueFor(String playerName, String club) {
        String who = playerName == null ? "this player" : playerName;
        if (club == null || club.isBlank()) {
            return "Argue for signing " + who + " using the save data. Be honest about unknown asking prices.";
        }
        return "For " + club + ", argue for signing " + who
                + ". Use the save data. Be honest about unknown asking prices and injuries.";
    }

    static String explainDeal(String playerName, String club) {
        return "For " + club + ", explain this Moneyball row for " + playerName
                + ": signing rating, deal tier, fee plus 3-year wages, and whether the deal is cheap.";
    }

    static String explainHole(String position, String club) {
        return "For " + club + ", why is " + position + " a hole in the first XI, and who covers?";
    }

    static String boardNote(String playerName, String action, String club) {
        return "For " + club + ", draft a short board note to " + action + " " + playerName
                + " using contract, wage and squad depth from the save.";
    }
}
