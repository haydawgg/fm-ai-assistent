package com.github.fmaiassistent.memory;

/** Explicit result for a live-memory read; unknown is not the same as zero. */
public record MemoryReadResult<T>(State state, T value, String message) {
    public enum State {
        KNOWN,
        UNKNOWN,
        ERROR
    }

    public MemoryReadResult {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        message = message == null ? "" : message;
    }

    public static <T> MemoryReadResult<T> known(T value) {
        return new MemoryReadResult<>(State.KNOWN, value, "");
    }

    public static <T> MemoryReadResult<T> unknown(String message) {
        return new MemoryReadResult<>(State.UNKNOWN, null, message);
    }

    public static <T> MemoryReadResult<T> error(String message) {
        return new MemoryReadResult<>(State.ERROR, null, message);
    }

    public boolean known() {
        return state == State.KNOWN;
    }
}
