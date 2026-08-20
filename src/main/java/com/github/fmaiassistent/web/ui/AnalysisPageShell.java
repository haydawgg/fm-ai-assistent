package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Shared heading seam for analysis routes with a consistent scope and hierarchy. */
public class AnalysisPageShell extends Div {
    public AnalysisPageShell(String title, String hint) {
        addClassName("analysis-page-shell");
        Span eyebrow = new Span("Decision workspace");
        eyebrow.addClassName("workspace-eyebrow");
        Span heading = new Span(title);
        heading.addClassName("workspace-title");
        Span description = new Span(hint);
        description.addClassName("moneyball-hint");
        add(eyebrow, heading, description);
    }
}
