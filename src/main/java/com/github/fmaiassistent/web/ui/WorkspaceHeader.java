package com.github.fmaiassistent.web.ui;

/** Compact, consistent heading for the decision workspaces. */
public final class WorkspaceHeader extends AnalysisPageShell {

    public WorkspaceHeader(String title, String hint) {
        super(title, hint);
        addClassName("workspace-header");
    }
}
