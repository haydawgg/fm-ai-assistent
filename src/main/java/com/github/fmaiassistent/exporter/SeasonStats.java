package com.github.fmaiassistent.exporter;

/** Current-season all-competition statistics read for one player. */
public record SeasonStats(
        Integer appearances,
        Integer starts,
        Integer minutes,
        Integer goals,
        Integer assists,
        Double averageRating) {

    public static SeasonStats unknown() {
        return new SeasonStats(null, null, null, null, null, null);
    }

    public boolean isKnown() {
        return appearances != null || starts != null || minutes != null
                || goals != null || assists != null || averageRating != null;
    }

    public boolean isValid() {
        return nonNegative(appearances) && nonNegative(starts) && nonNegative(minutes)
                && nonNegative(goals) && nonNegative(assists)
                && (averageRating == null || (averageRating >= 0.0 && averageRating <= 10.0))
                && (goals == null || minutes == null || goals <= minutes)
                && (assists == null || minutes == null || assists <= minutes);
    }

    private static boolean nonNegative(Integer value) {
        return value == null || value >= 0;
    }
}
