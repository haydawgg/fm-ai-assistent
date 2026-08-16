package com.github.fmaiassistent.linux;

import java.util.List;
import java.util.Optional;

public final class FmFormations {
    private FmFormations() {
    }

    public record Shape(int code, String name, List<String> positions) {
        public String slotText() {
            StringBuilder out = new StringBuilder();
            for (String position : positions) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(position).append(",,");
            }
            return out.toString();
        }
    }

    public static Optional<Shape> shape(int code) {
        String name = name(code);
        if (name == null) {
            return Optional.empty();
        }
        List<String> positions = positions(code);
        if (positions.size() != 11) {
            return Optional.empty();
        }
        return Optional.of(new Shape(code, name, positions));
    }

    public static boolean knownCode(int code) {
        return name(code) != null;
    }

    static String name(int code) {
        return switch (code) {
            case 2 -> "5-3-2 WB";
            case 3 -> "4-4-2";
            case 4 -> "4-1-2-3 DM Wide";
            case 5 -> "4-2-4 Wide";
            case 6 -> "3-5-2";
            case 7 -> "4-1-2-1-2 Diamond Narrow";
            case 8 -> "4-5-1";
            case 9 -> "3-4-3";
            case 10 -> "3-4-1-2";
            case 11 -> "3-4-2-1";
            case 12 -> "4-3-2-1 Narrow";
            case 14 -> "4-3-1-2 Narrow";
            case 15 -> "5-4-1 Diamond WB";
            case 16 -> "4-4-1-1";
            case 18 -> "4-2-3-1 Narrow";
            case 21 -> "4-2-3-1 Wide";
            case 22 -> "4-2-2-2 DM";
            case 24 -> "4-2-2-2 DM Narrow";
            case 26 -> "4-1-2-3 DM Narrow";
            case 27 -> "4-1-2-2-1 DM Narrow";
            case 28 -> "4-2-4 DM Wide";
            case 29, 54 -> "4-2-3-1 DM Wide";
            case 32 -> "4-1-4-1 DM";
            case 36 -> "4-3-3 Narrow";
            case 37 -> "4-2-4";
            case 38, 57 -> "5-4-1";
            case 39 -> "4-1-2-3 Narrow";
            case 40 -> "4-2-3-1 DM Narrow";
            case 41, 64 -> "5-2-1-2 WB";
            case 59 -> "5-2-2-1 WB";
            default -> null;
        };
    }

    private static List<String> positions(int code) {
        return switch (code) {
            case 3 -> List.of("GK", "DR", "DC", "DC", "DL", "MR", "MC", "MC", "ML", "ST", "ST");
            case 4 -> List.of("GK", "DR", "DC", "DC", "DL", "DMC", "MC", "MC", "AMR", "AML", "ST");
            case 5, 28, 37 -> List.of("GK", "DR", "DC", "DC", "DL", "MC", "MC", "AMR", "AML", "ST", "ST");
            case 6 -> List.of("GK", "DC", "DC", "DC", "MR", "MC", "MC", "MC", "ML", "ST", "ST");
            case 7 -> List.of("GK", "DR", "DC", "DC", "DL", "DMC", "MC", "MC", "AMC", "ST", "ST");
            case 8, 32 -> List.of("GK", "DR", "DC", "DC", "DL", "MR", "MC", "MC", "MC", "ML", "ST");
            case 9 -> List.of("GK", "DC", "DC", "DC", "MR", "MC", "MC", "ML", "AMR", "AML", "ST");
            case 10 -> List.of("GK", "DC", "DC", "DC", "MR", "MC", "MC", "ML", "AMC", "ST", "ST");
            case 11 -> List.of("GK", "DC", "DC", "DC", "MR", "MC", "MC", "ML", "AMC", "AMC", "ST");
            case 12 -> List.of("GK", "DR", "DC", "DC", "DL", "MC", "MC", "MC", "AMC", "AMC", "ST");
            case 14 -> List.of("GK", "DR", "DC", "DC", "DL", "MC", "MC", "MC", "AMC", "ST", "ST");
            case 16 -> List.of("GK", "DR", "DC", "DC", "DL", "MR", "MC", "MC", "ML", "AMC", "ST");
            case 18, 40 -> List.of("GK", "DR", "DC", "DC", "DL", "MC", "MC", "AMC", "AMC", "AMC", "ST");
            case 21, 29, 54 -> List.of("GK", "DR", "DC", "DC", "DL", "MC", "MC", "AMR", "AMC", "AML", "ST");
            case 22 -> List.of("GK", "DR", "DC", "DC", "DL", "DMC", "DMC", "AMR", "AML", "ST", "ST");
            case 24 -> List.of("GK", "DR", "DC", "DC", "DL", "DMC", "DMC", "AMC", "AMC", "ST", "ST");
            case 26, 39 -> List.of("GK", "DR", "DC", "DC", "DL", "DMC", "MC", "MC", "ST", "ST", "ST");
            case 27 -> List.of("GK", "DR", "DC", "DC", "DL", "DMC", "MC", "MC", "AMC", "AMC", "ST");
            case 2 -> List.of("GK", "WBR", "DC", "DC", "DC", "WBL", "MC", "MC", "MC", "ST", "ST");
            case 15 -> List.of("GK", "WBR", "DC", "DC", "DC", "WBL", "DMC", "MC", "MC", "AMC", "ST");
            case 38, 57 -> List.of("GK", "DR", "DC", "DC", "DC", "DL", "MR", "MC", "MC", "ML", "ST");
            case 41, 64 -> List.of("GK", "WBR", "DC", "DC", "DC", "WBL", "MC", "MC", "AMC", "ST", "ST");
            case 59 -> List.of("GK", "WBR", "DC", "DC", "DC", "WBL", "MC", "MC", "AMR", "AML", "ST");
            case 36 -> List.of("GK", "DR", "DC", "DC", "DL", "MC", "MC", "MC", "ST", "ST", "ST");
            default -> List.of();
        };
    }
}
