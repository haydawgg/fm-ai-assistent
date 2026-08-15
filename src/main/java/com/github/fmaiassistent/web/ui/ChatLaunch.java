package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
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

    static Button askButton(String playerName, String club) {
        String who = playerName == null || playerName.isBlank() ? "this player" : playerName;
        Button button = new Button(VaadinIcon.CHAT.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.setTooltipText("Ask FM AI about " + who);
        button.getElement().setAttribute("aria-label", "Ask FM AI about " + who);
        button.addClickListener(event -> open(askAbout(playerName, club)));
        return button;
    }

    static String askAbout(String playerName, String club) {
        String who = playerName == null ? "this player" : playerName;
        if (club == null || club.isBlank()) {
            return "Using the save data, tell me about " + who
                    + ": role, contract, injuries and whether they are a buy, sell, or keep.";
        }
        return "For " + club + ", tell me about " + who
                + " using the save data: role, contract, injuries and whether they are a buy, sell, or keep.";
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
