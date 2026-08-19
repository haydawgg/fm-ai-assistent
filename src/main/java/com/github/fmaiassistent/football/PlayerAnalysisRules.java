package com.github.fmaiassistent.football;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.linux.GameDateFinder;

import java.time.Period;
import java.util.List;
import java.util.Locale;

/**
 * Deep, transport-independent rules used by player recruitment and squad analysis.
 *
 * <p>This module owns the meaning of unknown values and the normalization rules
 * shared by the MCP adapter and the desktop views. It has no database, Vaadin,
 * or Spring dependencies, so the rules can be exercised with deterministic
 * player fixtures.</p>
 */
public final class PlayerAnalysisRules {
    private PlayerAnalysisRules() {
    }

    public static boolean inRange(Integer value, Integer min, Integer max) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return (min == null || value >= min) && (max == null || value <= max);
    }

    /** Unknown asking prices are not free; free agents may still pass a price filter. */
    public static boolean askingPriceWithinMax(Long askingPrice, String club, Long max) {
        if (max == null) {
            return true;
        }
        if (askingPrice == null || askingPrice <= 0) {
            return blank(club);
        }
        return askingPrice <= max;
    }

    public static boolean salaryWithinMax(Integer salaryWeekly, Integer max) {
        return max == null || salaryWeekly != null && salaryWeekly <= max;
    }

    public static boolean wageFits(Integer salaryWeekly, long wageCeiling) {
        return salaryWeekly != null && salaryWeekly <= wageCeiling;
    }

    public static long resolvePriceCap(Long maxAskingPrice, Long budget) {
        if (maxAskingPrice != null) {
            return Math.max(0L, maxAskingPrice);
        }
        return budget == null ? Long.MAX_VALUE : Math.max(0L, budget);
    }

    public static boolean priceCapKnown(long priceCap) {
        return priceCap != Long.MAX_VALUE;
    }

    public static boolean rolesMatch(String catalogName, String query) {
        if (blank(query)) {
            return true;
        }
        String catalog = roleKey(catalogName);
        String needle = roleKey(query);
        return !catalog.isEmpty() && !needle.isEmpty()
                && (catalog.equals(needle) || catalog.contains(needle) || needle.contains(catalog));
    }

    public static boolean roleKeysEqual(String left, String right) {
        return roleKey(left).equals(roleKey(right));
    }

    public static String roleKey(String value) {
        String normalized = normalize(value).replaceAll("\\bgk\\b", "goalkeeper");
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    public static boolean matchesBoolean(Boolean value, Boolean expected) {
        return expected == null || value != null && value.equals(expected);
    }

    public static Integer effectiveAge(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return GameDateFinder.effectiveAge(player.getAge(), player.getDateOfBirth(), player.getAgeAsOf());
    }

    public static boolean recentlyJoinedCurrentClub(PlayerEntity player, Period minimumTimeAtCurrentClub) {
        if (player == null || minimumTimeAtCurrentClub == null) {
            return false;
        }
        java.time.LocalDate joined = parseDate(player.getJoinedClubDate());
        java.time.LocalDate gameDate = parseDate(player.getAgeAsOf());
        return joined != null && gameDate != null
                && joined.isAfter(gameDate.minus(minimumTimeAtCurrentClub));
    }

    public static boolean dropUnwillingCandidate(
            boolean dropUnwilling, boolean lowWillingness, boolean priceKnown, boolean freeAgent) {
        return lowWillingness && (dropUnwilling || (!priceKnown && !freeAgent));
    }

    public static boolean inClubFamily(String playerClub, String managingClub) {
        String playerStem = clubFamilyStem(playerClub);
        String managingStem = clubFamilyStem(managingClub);
        return !playerStem.isEmpty() && playerStem.equals(managingStem);
    }

    public static String clubFamilyStem(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return "";
        }
        List<String> tokens = new java.util.ArrayList<>(List.of(normalized.split("\\s+")));
        java.util.Set<String> suffixes = java.util.Set.of(
                "u18", "u19", "u20", "u21", "u23", "ii", "iii", "2", "b",
                "reserve", "reserves", "amateur", "amateurs", "youth");
        while (tokens.size() > 1 && suffixes.contains(tokens.getLast())) {
            tokens.removeLast();
        }
        return String.join(" ", tokens);
    }

    private static java.time.LocalDate parseDate(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
