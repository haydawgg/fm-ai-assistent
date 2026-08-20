package com.github.fmaiassistent.web.ui;

/** Small, explicit context passed from a decision surface to contextual AI. */
public record PlayerContext(String playerName, String club, String season, String snapshotReadAt) {
}
