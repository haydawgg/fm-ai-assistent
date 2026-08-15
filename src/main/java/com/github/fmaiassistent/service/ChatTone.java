package com.github.fmaiassistent.service;

public enum ChatTone {
    CONCISE("Concise", 0.2, "Answer concisely. Prefer short bullets over long prose."),
    DETAILED("Detailed", 0.7, "Answer in useful detail. Explain trade-offs."),
    BOARD("Board report", 0.3, "Write a short board report: recommendation, cost, risk, next action.");

    private final String label;
    private final double temperature;
    private final String instruction;

    ChatTone(String label, double temperature, String instruction) {
        this.label = label;
        this.temperature = temperature;
        this.instruction = instruction;
    }

    public String label() {
        return label;
    }

    public double temperature() {
        return temperature;
    }

    public String instruction() {
        return instruction;
    }

    public static ChatTone fromProperty(String value) {
        if (value == null || value.isBlank()) {
            return DETAILED;
        }
        try {
            return ChatTone.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DETAILED;
        }
    }
}
