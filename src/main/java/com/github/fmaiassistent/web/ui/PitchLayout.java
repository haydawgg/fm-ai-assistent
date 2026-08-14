package com.github.fmaiassistent.web.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PitchLayout {
    private PitchLayout() {
    }

    record Slot(int index, String position, double xPercent, double yPercent) {
    }

    static List<Slot> layout(List<String> positions) {
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        for (String raw : positions) {
            String code = code(raw);
            totals.merge(code, 1, Integer::sum);
        }
        List<Slot> slots = new ArrayList<>();
        for (int index = 0; index < positions.size(); index++) {
            String code = code(positions.get(index));
            int occurrence = seen.merge(code, 1, Integer::sum) - 1;
            int total = totals.getOrDefault(code, 1);
            double[] base = base(code);
            slots.add(new Slot(index, code, spread(base[0], occurrence, total), base[1]));
        }
        return slots;
    }

    private static String code(String raw) {
        return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    }

    private static double[] base(String code) {
        return switch (code) {
            case "GK" -> new double[] {50, 90};
            case "DL" -> new double[] {14, 72};
            case "DC" -> new double[] {50, 74};
            case "DR" -> new double[] {86, 72};
            case "WBL" -> new double[] {10, 60};
            case "DMC" -> new double[] {50, 60};
            case "WBR" -> new double[] {90, 60};
            case "ML" -> new double[] {14, 48};
            case "MC" -> new double[] {50, 48};
            case "MR" -> new double[] {86, 48};
            case "AML" -> new double[] {18, 28};
            case "AMC" -> new double[] {50, 28};
            case "AMR" -> new double[] {82, 28};
            case "ST" -> new double[] {50, 12};
            default -> new double[] {50, 50};
        };
    }

    static double spread(double center, int index, int total) {
        if (total <= 1) {
            return center;
        }
        double span = Math.min(40, 16.0 * (total - 1));
        double start = center - span / 2.0;
        return start + span * index / (total - 1.0);
    }
}
