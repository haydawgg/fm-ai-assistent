package com.github.fmaiassistent.service;

/**
 * Central limits for chat content persisted in CLOB-backed columns.
 *
 * <p>Keeping the limits here gives the UI and persistence boundary one
 * contract, so a non-browser caller cannot bypass the database safety limit.
 */
public final class ChatContentLimits {
    public static final int MAX_MESSAGE_CHARACTERS = 100_000;
    public static final int MAX_TOOLS_JSON_CHARACTERS = 100_000;
    public static final int MAX_REASONING_CHARACTERS = 100_000;

    private ChatContentLimits() {
    }

    public static String requireMessage(String body) {
        return requireWithinLimit("message", body, MAX_MESSAGE_CHARACTERS);
    }

    public static String requireToolsJson(String toolsJson) {
        return requireWithinLimit("tool trace", toolsJson, MAX_TOOLS_JSON_CHARACTERS);
    }

    public static String requireReasoning(String reasoning) {
        return requireWithinLimit("reasoning", reasoning, MAX_REASONING_CHARACTERS);
    }

    private static String requireWithinLimit(String label, String value, int limit) {
        if (value != null && value.length() > limit) {
            throw new IllegalArgumentException(
                    label + " exceeds the maximum size of " + limit + " characters");
        }
        return value;
    }
}
