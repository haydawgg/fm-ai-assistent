package com.github.fmaiassistent.web.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/** Reusable trust/status surface for snapshot-backed workspaces. */
public final class SnapshotStatusView extends Div {
    public SnapshotStatusView(SnapshotStatusModel model, Runnable refreshAction) {
        addClassName("snapshot-status-view");
        addClassName("snapshot-status-" + model.state().name().toLowerCase());
        getElement().setAttribute("role", "status");
        getElement().setAttribute("aria-live", "polite");

        Span state = new Span(stateLabel(model.state()));
        state.addClassName("snapshot-status-state");
        Span summary = new Span(model.playerCount() + " players" +
                (model.season().isBlank() ? "" : " · " + model.season()));
        summary.addClassName("snapshot-status-summary");
        Span detail = new Span(model.detail());
        detail.addClassName("snapshot-status-detail");
        add(state, summary, detail);

        if (refreshAction != null && model.state() != WorkspaceLoadState.LOADING) {
            Button refresh = new Button(model.state() == WorkspaceLoadState.NO_SNAPSHOT ? "Load from FM" : "Refresh",
                    VaadinIcon.REFRESH.create(), event -> refreshAction.run());
            refresh.addThemeVariants(model.state() == WorkspaceLoadState.NO_SNAPSHOT
                    ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
            refresh.addClassName("snapshot-status-action");
            refresh.setTooltipText("Refresh the published FM26 snapshot");
            add(refresh);
        }
    }

    private static String stateLabel(WorkspaceLoadState state) {
        return switch (state) {
            case NO_SNAPSHOT -> "No snapshot";
            case LOADING -> "Loading";
            case READY -> "Ready";
            case STALE -> "Stale";
            case PARTIAL -> "Partial";
            case ERROR -> "Refresh failed";
        };
    }
}
