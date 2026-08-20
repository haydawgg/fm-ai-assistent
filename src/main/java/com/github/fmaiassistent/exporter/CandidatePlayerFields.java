package com.github.fmaiassistent.exporter;

/** Optional native fields that are only populated by a validated build layout. */
public record CandidatePlayerFields(
        Long sourceUid,
        Integer morale,
        Integer condition,
        Long guideValue,
        Long transferValue,
        State state) {
    public CandidatePlayerFields {
        state = state == null ? State.UNAVAILABLE : state;
        if (sourceUid != null && sourceUid <= 0) {
            sourceUid = null;
        }
        if (!range(morale, 0, 100)) morale = null;
        if (!range(condition, 0, 100)) condition = null;
        if (!range(guideValue, 0L, 1_000_000_000_000L)) guideValue = null;
        if (!range(transferValue, 0L, 1_000_000_000_000L)) transferValue = null;
        if (state == State.AVAILABLE && anyNull(sourceUid, morale, condition, guideValue, transferValue)) {
            state = State.PARTIAL;
        } else if (state == State.PARTIAL && allNull(sourceUid, morale, condition, guideValue, transferValue)) {
            state = State.UNAVAILABLE;
        }
    }

    public static CandidatePlayerFields unknown() {
        return new CandidatePlayerFields(null, null, null, null, null, State.UNAVAILABLE);
    }

    private static boolean range(Number value, long min, long max) {
        return value == null || (value.longValue() >= min && value.longValue() <= max);
    }

    private static boolean anyNull(Object... values) {
        for (Object value : values) if (value == null) return true;
        return false;
    }

    private static boolean allNull(Object... values) {
        for (Object value : values) if (value != null) return false;
        return true;
    }

    public enum State { AVAILABLE, PARTIAL, UNAVAILABLE }
}
