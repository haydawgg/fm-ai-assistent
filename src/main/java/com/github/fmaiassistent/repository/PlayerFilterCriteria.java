package com.github.fmaiassistent.repository;

import java.time.LocalDate;
import java.util.Map;

public record PlayerFilterCriteria(
        String name,
        String gender,
        String playingNation,
        String playingCompetition,
        String club,
        Integer ageMin,
        Integer ageMax,
        Integer heightMin,
        Integer heightMax,
        String nationality,
        Integer currentReputationMin,
        Integer currentReputationMax,
        Integer homeReputationMin,
        Integer homeReputationMax,
        Integer worldReputationMin,
        Integer worldReputationMax,
        Integer caMin,
        Integer caMax,
        Integer paMin,
        Integer paMax,
        LocalDate contractEndDateFrom,
        LocalDate contractEndDateTo,
        Long askingPriceMin,
        Long askingPriceMax,
        Long salaryMax,
        Map<String, Integer> positionMinimums,
        Map<String, Integer> attributeMinimums) {
    public PlayerFilterCriteria {
        positionMinimums = positionMinimums == null ? Map.of() : positionMinimums;
        attributeMinimums = attributeMinimums == null ? Map.of() : attributeMinimums;
    }

    public static PlayerFilterCriteria clubOnly(String club) {
        return empty().withClub(club);
    }

    public PlayerFilterCriteria withClub(String club) {
        return new PlayerFilterCriteria(
                name, gender, playingNation, playingCompetition, club == null ? "" : club,
                ageMin, ageMax, heightMin, heightMax, nationality,
                currentReputationMin, currentReputationMax, homeReputationMin, homeReputationMax,
                worldReputationMin, worldReputationMax, caMin, caMax, paMin, paMax,
                contractEndDateFrom, contractEndDateTo, askingPriceMin, askingPriceMax, salaryMax,
                positionMinimums, attributeMinimums);
    }

    public boolean isClubOnly() {
        return !isBlank(club)
                && isBlank(name)
                && isBlank(gender)
                && isBlank(playingNation)
                && isBlank(playingCompetition)
                && ageMin == null
                && ageMax == null
                && heightMin == null
                && heightMax == null
                && isBlank(nationality)
                && currentReputationMin == null
                && currentReputationMax == null
                && homeReputationMin == null
                && homeReputationMax == null
                && worldReputationMin == null
                && worldReputationMax == null
                && caMin == null
                && caMax == null
                && paMin == null
                && paMax == null
                && contractEndDateFrom == null
                && contractEndDateTo == null
                && askingPriceMin == null
                && askingPriceMax == null
                && salaryMax == null
                && positionMinimums.isEmpty()
                && attributeMinimums.isEmpty();
    }

    public static PlayerFilterCriteria empty() {
        return new PlayerFilterCriteria(
                "", "", "", "", "", null, null, null, null, "", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                Map.of(), Map.of());
    }

    public String chatSummary() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        addPart(parts, "name", name);
        addPart(parts, "gender", gender);
        addPart(parts, "nation", playingNation);
        addPart(parts, "competition", playingCompetition);
        addPart(parts, "club", club);
        addPart(parts, "nationality", nationality);
        if (ageMin != null || ageMax != null) {
            parts.add("age " + range(ageMin, ageMax));
        }
        if (caMin != null || caMax != null) {
            parts.add("CA " + range(caMin, caMax));
        }
        if (paMin != null || paMax != null) {
            parts.add("PA " + range(paMin, paMax));
        }
        if (askingPriceMax != null) {
            parts.add("fee ≤ " + askingPriceMax);
        }
        if (salaryMax != null) {
            parts.add("wage ≤ " + salaryMax);
        }
        if (positionMinimums != null && !positionMinimums.isEmpty()) {
            parts.add("positions " + positionMinimums);
        }
        return String.join(", ", parts);
    }

    private static void addPart(java.util.List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + " " + value.strip());
        }
    }

    private static String range(Integer min, Integer max) {
        if (min != null && max != null) {
            return min + "–" + max;
        }
        if (min != null) {
            return "≥ " + min;
        }
        return "≤ " + max;
    }

    public boolean isEmpty() {
        return isBlank(name)
                && isBlank(gender)
                && isBlank(playingNation)
                && isBlank(playingCompetition)
                && isBlank(club)
                && ageMin == null
                && ageMax == null
                && heightMin == null
                && heightMax == null
                && isBlank(nationality)
                && currentReputationMin == null
                && currentReputationMax == null
                && homeReputationMin == null
                && homeReputationMax == null
                && worldReputationMin == null
                && worldReputationMax == null
                && caMin == null
                && caMax == null
                && paMin == null
                && paMax == null
                && contractEndDateFrom == null
                && contractEndDateTo == null
                && askingPriceMin == null
                && askingPriceMax == null
                && salaryMax == null
                && positionMinimums.isEmpty()
                && attributeMinimums.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
