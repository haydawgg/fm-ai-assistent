package com.github.fmaiassistent.web.ui;

/** Focused player-desk lenses that keep the core decision fields readable. */
public enum PlayerViewPreset {
    SQUAD("Squad", "Ability, availability, and squad context"),
    RECRUITMENT("Recruitment", "Value, upside, and transfer signals"),
    CONTRACTS("Contracts", "Wages, asking price, and contract risk"),
    PERFORMANCE("Performance", "Current-season output and involvement"),
    FULL_DATA("Full data", "Every available player field");

    private final String label;
    private final String description;

    PlayerViewPreset(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return label;
    }
}
