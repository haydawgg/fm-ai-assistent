package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** Compact, consistent heading for the decision workspaces. */
public final class WorkspaceHeader extends Div {

    public WorkspaceHeader(String title, String hint) {
        addClassName("workspace-header");

        Span eyebrow = new Span("Decision workspace");
        eyebrow.addClassName("workspace-eyebrow");

        Span heading = new Span(title);
        heading.addClassName("workspace-title");

        Span description = new Span(hint);
        description.addClassName("moneyball-hint");

        add(eyebrow, heading, description);
    }
}
