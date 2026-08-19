package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.domain.enums.MoneyCurrency;
import com.github.fmaiassistent.football.PlayerAnalysisPort;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
import com.github.fmaiassistent.mcp.Positions;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PlayerDossier {
    private PlayerDossier() {
    }

    static void openNamed(PlayerAnalysisPort tools, String name, MoneyCurrency currency, String sessionClub) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            open(tools.playerByName(name), currency, sessionClub);
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage() == null ? "Player not found" : ex.getMessage(),
                    4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    static void open(PlayerEntity player, MoneyCurrency currency, String sessionClub) {
        if (player == null) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Player dossier");
        dialog.setWidth("720px");
        dialog.getElement().getThemeList().add("professional-dialog");
        dialog.getElement().getThemeList().add("player-dossier-dialog");

        Button chat = new Button("Ask FM AI about " + (player.getName() == null ? "player" : player.getName()),
                VaadinIcon.CHAT.create());
        chat.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        chat.addClickListener(event -> {
            dialog.close();
            ChatLaunch.open(ChatLaunch.askAbout(player.getName(), sessionClub));
        });
        Button close = new Button("Close", event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(chat, close);

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(true);
        body.addClassName("player-dossier");
        body.add(hero(player, currency));
        body.add(summary(player, currency));
        body.add(note("Morale, form and match stats are not read from RAM yet. Asking price 0 is unknown, not free."));
        body.add(section("Profile", List.of(
                field("Position", PositionTextFormatter.format(player)),
                field("Club", player.getClub()),
                field("Playing club", player.getPlayingClub()),
                field("Nationality", player.getNationality()),
                field("Age", player.getAge()),
                field("Traits", blank(player.getTraits()) ? "—" : player.getTraits()))));
        body.add(section("Contract", List.of(
                field("Wage / wk", MoneyDisplay.format(nz(player.getSalaryWeeklyRaw()), currency)),
                field("Asking price", asking(player, currency)),
                field("Contract end", blank(player.getContractEndDate()) ? "—" : player.getContractEndDate()),
                field("Joined", blank(player.getJoinedClubDate()) ? "—" : player.getJoinedClubDate()))));
        body.add(section("Injury", List.of(
                field("Status", player.getInjured() == null
                        ? "Unknown"
                        : Boolean.TRUE.equals(player.getInjured()) ? injured(player) : "Fit"),
                field("Expected return", blank(player.getInjuryExpectedReturn()) ? "—" : player.getInjuryExpectedReturn()))));
        body.add(attributes(player));
        dialog.add(body);
        dialog.open();
    }

    private static Div hero(PlayerEntity player, MoneyCurrency currency) {
        String name = blank(player.getName()) ? "Unknown player" : player.getName();
        Span title = new Span(name);
        title.addClassName("dossier-hero-title");
        String club = blank(player.getPlayingClub()) ? player.getClub() : player.getPlayingClub();
        Span meta = new Span((blank(club) ? "Club unknown" : club) + " · " + PositionTextFormatter.format(player));
        meta.addClassName("dossier-hero-meta");
        Span status = new Span(player.getInjured() == null ? "Availability unknown"
                : Boolean.TRUE.equals(player.getInjured()) ? injured(player) : "Available");
        status.addClassName(Boolean.TRUE.equals(player.getInjured()) ? "dossier-availability is-unavailable" : "dossier-availability");
        Div hero = new Div(title, meta, status);
        hero.addClassName("dossier-hero");
        return hero;
    }

    private static Div summary(PlayerEntity player, MoneyCurrency currency) {
        Div row = new Div(
                chip("CA", String.valueOf(nz(player.getCa()))),
                chip("PA", String.valueOf(nz(player.getPa()))),
                chip("Age", blank(player.getAge()) ? "—" : player.getAge()),
                chip("Pos", Positions.bestCode(player) == null ? "—" : Positions.bestCode(player)),
                chip("Wage", MoneyDisplay.format(nz(player.getSalaryWeeklyRaw()), currency)),
                chip("Asking", asking(player, currency)));
        row.addClassName("player-summary");
        return row;
    }

    private static VerticalLayout attributes(PlayerEntity player) {
        List<int[]> scored = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (FieldDef field : AttributeDefinitions.VISIBLE_FIELDS) {
            Object raw = player.getColumnValue(columnName(field.name()));
            if (!(raw instanceof Number number)) {
                continue;
            }
            int value = number.intValue();
            if (value <= 0) {
                continue;
            }
            labels.add(field.name());
            scored.add(new int[] {labels.size() - 1, value});
        }
        scored.sort(Comparator.comparingInt((int[] row) -> row[1]).reversed());
        Div shape = new Div();
        shape.addClassName("dossier-attrs");
        int shown = 0;
        for (int[] row : scored) {
            if (shown >= 12) {
                break;
            }
            Div item = new Div();
            item.addClassName("dossier-attr");
            Span name = new Span(labels.get(row[0]));
            name.addClassName("detail-label");
            Span value = new Span(String.valueOf(row[1]));
            value.addClassName("detail-value");
            item.add(name, value);
            shape.add(item);
            shown++;
        }
        if (shown == 0) {
            shape.add(note("No attributes in this snapshot."));
        }
        VerticalLayout wrap = new VerticalLayout(heading("Attribute shape"), shape);
        wrap.setPadding(false);
        wrap.setSpacing(false);
        return wrap;
    }

    private static VerticalLayout section(String title, List<Div> fields) {
        Div grid = new Div();
        grid.addClassName("dossier-grid");
        fields.forEach(grid::add);
        VerticalLayout wrap = new VerticalLayout(heading(title), grid);
        wrap.setPadding(false);
        wrap.setSpacing(false);
        return wrap;
    }

    private static Span heading(String text) {
        Span heading = new Span(text);
        heading.addClassName("first-xi-heading");
        return heading;
    }

    private static Span note(String text) {
        Span note = new Span(text);
        note.addClassName("moneyball-hint");
        return note;
    }

    private static Div field(String label, String value) {
        Span labelText = new Span(label);
        labelText.addClassName("detail-label");
        Span valueText = new Span(value == null || value.isBlank() ? "—" : value);
        valueText.addClassName("detail-value");
        Div field = new Div(labelText, valueText);
        field.addClassName("detail-field");
        return field;
    }

    private static Div chip(String label, String value) {
        Span labelText = new Span(label);
        Span valueText = new Span(value);
        valueText.addClassName("status-chip-value");
        Div chip = new Div(labelText, valueText);
        chip.addClassName("status-chip");
        return chip;
    }

    private static String asking(PlayerEntity player, MoneyCurrency currency) {
        boolean freeAgent = player.getClub() == null || player.getClub().isBlank();
        Long asking = FmAiAssistentTools.askingPriceOrNull(player.getAskingPrice());
        if (freeAgent) {
            return "Free agent";
        }
        if (asking == null) {
            return "Unknown";
        }
        return MoneyDisplay.format(asking, currency);
    }

    private static String injured(PlayerEntity player) {
        String injury = blank(player.getInjury()) ? "Injured" : player.getInjury();
        Integer min = player.getInjuryMinDaysRemaining();
        if (min == null) {
            return injury;
        }
        Integer max = player.getInjuryMaxDaysRemaining();
        if (max != null && !max.equals(min)) {
            return injury + " · back in " + min + "–" + max + " days";
        }
        return injury + " · back in " + min + " days";
    }

    private static String columnName(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                out.append('_');
            }
            out.append(Character.toUpperCase(ch));
        }
        return out.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
