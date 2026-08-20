package com.github.fmaiassistent.web.ui;

import com.github.fmaiassistent.football.FirstXiPick;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PitchBoard extends Div {
    PitchBoard() {
        addClassName("pitch-board");
        getElement().setAttribute("role", "img");
        getElement().setAttribute("aria-label", "Match-week first XI on a pitch");
        setEmpty();
    }

    void setEmpty() {
        removeAll();
        Span hint = new Span("Pick your club in the top bar to place an XI on the pitch.");
        hint.addClassName("pitch-board-empty");
        add(hint);
    }

    void show(List<FirstXiPick> picks) {
        show(picks, Map.of());
    }

    void show(List<FirstXiPick> picks, Map<String, String> injuryNotes) {
        removeAll();
        if (picks == null || picks.isEmpty()) {
            setEmpty();
            return;
        }
        Div grass = new Div();
        grass.addClassName("pitch-grass");
        grass.add(mark("pitch-halfway"), mark("pitch-box-top"), mark("pitch-box-bottom"), mark("pitch-spot"));
        List<String> positions = picks.stream().map(FirstXiPick::position).toList();
        List<PitchLayout.Slot> slots = PitchLayout.layout(positions);
        for (PitchLayout.Slot slot : slots) {
            FirstXiPick pick = picks.get(slot.index());
            String note = injuryNote(pick.playerName(), injuryNotes);
            Div token = new Div();
            token.addClassName("pitch-token");
            if (pick.hole()) {
                token.addClassName("pitch-token-hole");
            } else if (!note.isBlank()) {
                token.addClassName("pitch-token-injured");
            }
            token.getStyle().set("left", slot.xPercent() + "%");
            token.getStyle().set("top", slot.yPercent() + "%");
            Span pos = new Span(pick.position());
            pos.addClassName("pitch-token-pos");
            Span name = new Span(pick.hole() || pick.playerName() == null ? "Hole" : pick.playerName());
            name.addClassName("pitch-token-name");
            token.add(pos, name);
            if (!note.isBlank()) {
                Span injury = new Span(note);
                injury.addClassName("pitch-token-injury");
                token.add(injury);
            }
            token.getElement().setAttribute("title", caption(pick, note));
            grass.add(token);
        }
        add(grass);
    }

    private static String injuryNote(String name, Map<String, String> injuryNotes) {
        if (name == null || injuryNotes == null || injuryNotes.isEmpty()) {
            return "";
        }
        String note = injuryNotes.get(name.strip().toLowerCase(Locale.ROOT));
        return note == null ? "" : note;
    }

    private static String caption(FirstXiPick pick, String note) {
        if (pick.hole()) {
            return pick.position() + " hole";
        }
        String base = pick.playerName() + " · " + pick.position();
        return note.isBlank() ? base : base + " · " + note;
    }

    private static Div mark(String className) {
        Div mark = new Div();
        mark.addClassName(className);
        return mark;
    }
}
