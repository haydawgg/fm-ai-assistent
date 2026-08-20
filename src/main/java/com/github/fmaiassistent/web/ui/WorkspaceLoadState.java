package com.github.fmaiassistent.web.ui;

/** Shared lifecycle states used by the manager workspaces. */
public enum WorkspaceLoadState {
    NO_SNAPSHOT,
    LOADING,
    READY,
    STALE,
    PARTIAL,
    ERROR
}
