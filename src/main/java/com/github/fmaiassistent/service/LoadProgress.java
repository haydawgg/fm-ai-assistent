package com.github.fmaiassistent.service;

public record LoadProgress(Phase phase, long done, long total, long kept, String detail) {
    public enum Phase {
        COMPETITIONS,
        CLUBS,
        PEOPLE,
        SAVING
    }

    public LoadProgress(Phase phase, long done, long total, long kept) {
        this(phase, done, total, kept, "");
    }

    public double phaseFraction() {
        if (total <= 0) {
            return 0;
        }
        return Math.min(1.0, (double) done / (double) total);
    }

    public double overallFraction() {
        double base = switch (phase) {
            case COMPETITIONS -> 0.0;
            case CLUBS -> 0.05;
            case PEOPLE -> 0.20;
            case SAVING -> 0.80;
        };
        double span = switch (phase) {
            case COMPETITIONS -> 0.05;
            case CLUBS -> 0.15;
            case PEOPLE -> 0.60;
            case SAVING -> 0.20;
        };
        return Math.min(1.0, base + span * phaseFraction());
    }

    public String title() {
        return switch (phase) {
            case COMPETITIONS -> "Reading competitions…";
            case CLUBS -> "Reading clubs…";
            case PEOPLE -> "Reading people…";
            case SAVING -> "Saving players…";
        };
    }

    public String subtitle() {
        String unit = switch (phase) {
            case COMPETITIONS -> "competitions";
            case CLUBS -> "clubs";
            case PEOPLE, SAVING -> "players";
        };
        String counts = phase == Phase.SAVING
                ? done + " / " + total + " " + unit + " saved"
                : done + " / " + total + " slots · " + kept + " " + unit;
        if (detail == null || detail.isBlank()) {
            return counts;
        }
        return counts + detail;
    }
}
