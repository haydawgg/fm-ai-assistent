package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows the live selected XI stored in RAM-load metadata ({@code tactic_selected}).
 * Same snapshot as {@code fm26_current_tactic}; roles are not in RAM.
 */
public class LiveSelectedXiPanel extends VerticalLayout {

    public record SelectedSlot(String position, String playerName) {
    }

    public LiveSelectedXiPanel(Map<String, Object> metadata) {
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        addClassName("live-selected-xi");

        Span heading = new Span("Live selected XI from RAM");
        heading.addClassName("first-xi-heading");

        Grid<SelectedSlot> grid = new Grid<>();
        grid.addClassName("moneyball-grid");
        grid.setAllRowsVisible(true);
        grid.addColumn(SelectedSlot::position).setHeader("Pos").setWidth("4em").setFlexGrow(0);
        grid.addColumn(SelectedSlot::playerName).setHeader("Player");

        List<SelectedSlot> slots = parse(stringMeta(metadata, "tactic_selected"));
        if (slots.isEmpty()) {
            grid.setEmptyStateText("No selected XI in this snapshot. Load from RAM with FM26 running.");
            grid.setItems(List.of());
        } else {
            grid.setItems(slots);
        }

        String formation = stringMeta(metadata, "tactic_formation");
        Span note = new Span(formation.isBlank()
                ? "Same data as fm26_current_tactic. In/out-of-possession roles are not in RAM."
                : "Formation " + formation + " · same data as fm26_current_tactic. Roles are not in RAM.");
        note.addClassName("moneyball-summary");

        add(heading, note, grid);
    }

    static List<SelectedSlot> parse(String tacticSelected) {
        if (tacticSelected == null || tacticSelected.isBlank() || "null".equals(tacticSelected)) {
            return List.of();
        }
        List<SelectedSlot> slots = new ArrayList<>();
        for (String line : tacticSelected.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int comma = line.indexOf(',');
            if (comma < 0) {
                slots.add(new SelectedSlot("", line.trim()));
            } else {
                slots.add(new SelectedSlot(line.substring(0, comma).trim(), line.substring(comma + 1).trim()));
            }
        }
        return slots;
    }

    private static String stringMeta(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get(key);
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equals(text) ? "" : text;
    }
}
