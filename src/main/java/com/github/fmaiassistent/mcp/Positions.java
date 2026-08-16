package com.github.fmaiassistent.mcp;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.player.AttributeDefinitions;
import com.github.fmaiassistent.player.FieldDef;

import java.util.Locale;

public final class Positions {
    private Positions() {
    }

    public static String column(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String key = code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        return switch (key) {
            case "gk", "goalkeeper" -> "GOALKEEPER";
            case "dl", "defenderleft", "leftback", "lb" -> "DEFENDER_LEFT";
            case "dc", "cb", "defendercentral", "centraldefender", "centreback", "centerback" -> "DEFENDER_CENTRAL";
            case "dr", "defenderright", "rightback", "rb" -> "DEFENDER_RIGHT";
            case "wbl", "wingbackleft", "leftwingback", "lwb" -> "WING_BACK_LEFT";
            case "dmc", "dm", "cdm", "defensivemidfielder" -> "DEFENSIVE_MIDFIELDER";
            case "wbr", "wingbackright", "rightwingback", "rwb" -> "WING_BACK_RIGHT";
            case "ml", "midfielderleft", "leftmidfielder", "lm" -> "MIDFIELDER_LEFT";
            case "mc", "cm", "midfieldercentral", "centralmidfielder" -> "MIDFIELDER_CENTRAL";
            case "mr", "midfielderright", "rightmidfielder", "rm" -> "MIDFIELDER_RIGHT";
            case "aml", "attackingmidfielderleft", "leftwinger", "lw" -> "ATTACKING_MIDFIELDER_LEFT";
            case "amc", "am", "cam", "attackingmidfieldercentral", "attackingmidfielder" -> "ATTACKING_MIDFIELDER_CENTRAL";
            case "amr", "attackingmidfielderright", "rightwinger", "rw" -> "ATTACKING_MIDFIELDER_RIGHT";
            case "st", "striker", "forward", "cf", "centreforward", "centerforward" -> "STRIKER";
            default -> throw new IllegalArgumentException("unsupported position: " + code
                    + ". Use GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST");
        };
    }

    public static String canonicalCode(String position) {
        if (position == null || position.isBlank()) {
            return null;
        }
        String key = position.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        return switch (key) {
            case "gk", "goalkeeper" -> "GK";
            case "dl", "defenderleft", "leftback", "lb" -> "DL";
            case "dc", "cb", "dcr", "dcl", "sw", "defendercentral", "centraldefender", "centreback", "centerback" -> "DC";
            case "dr", "defenderright", "rightback", "rb" -> "DR";
            case "wbl", "wingbackleft", "leftwingback", "lwb" -> "WBL";
            case "dmc", "dm", "cdm", "dmr", "dml", "defensivemidfielder" -> "DMC";
            case "wbr", "wingbackright", "rightwingback", "rwb" -> "WBR";
            case "ml", "midfielderleft", "leftmidfielder", "lm" -> "ML";
            case "mc", "cm", "mcr", "mcl", "midfieldercentral", "centralmidfielder" -> "MC";
            case "mr", "midfielderright", "rightmidfielder", "rm" -> "MR";
            case "aml", "attackingmidfielderleft", "leftwinger", "lw" -> "AML";
            case "amc", "am", "cam", "amcr", "amcl", "attackingmidfieldercentral", "attackingmidfielder" -> "AMC";
            case "amr", "attackingmidfielderright", "rightwinger", "rw" -> "AMR";
            case "st", "str", "stc", "stl", "stcr", "stcl", "striker", "forward", "cf", "centreforward", "centerforward" -> "ST";
            default -> throw new IllegalArgumentException("unsupported position: " + position
                    + ". Use GK, DL, DC, DR, WBL, DMC, WBR, ML, MC, MR, AML, AMC, AMR or ST");
        };
    }

    public static String positionGroup(String code) {
        String canonical;
        try {
            canonical = canonicalCode(code);
        } catch (IllegalArgumentException ignored) {
            return "Unknown";
        }
        if (canonical == null) {
            return "Unknown";
        }
        return switch (canonical) {
            case "GK" -> "Goalkeeper";
            case "DL", "DR", "WBL", "WBR" -> "Full-Back / Wing-Back";
            case "DC" -> "Centre-Back";
            case "DMC" -> "Defensive Midfielder";
            case "ML", "MR", "AML", "AMR" -> "Wide Midfielder / Winger";
            case "MC" -> "Central Midfielder";
            case "AMC" -> "Attacking Midfielder";
            case "ST" -> "Striker";
            default -> throw new IllegalStateException("unhandled position: " + canonical);
        };
    }

    public static int score(PlayerEntity player, String position) {
        try {
            String canonical = canonicalCode(position);
            if (canonical == null) {
                return 0;
            }
            String column = column(canonical);
            Object value = player.getColumnValue(column);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    public static int bestScore(PlayerEntity player) {
        return AttributeDefinitions.POSITION_FIELDS.stream()
                .map(Positions::columnName)
                .map(player::getColumnValue)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToInt(Number::intValue)
                .max()
                .orElse(0);
    }

    public static String bestPosition(PlayerEntity player) {
        return bestCode(player);
    }

    public static String bestCode(PlayerEntity player) {
        String best = null;
        int bestScore = 0;
        for (String code : PositionCodes.CODES) {
            int score = score(player, code);
            if (score > bestScore) {
                bestScore = score;
                best = code;
            }
        }
        return best;
    }

    static String columnName(FieldDef field) {
        return FmAiAssistentTools.columnName(field);
    }
}
