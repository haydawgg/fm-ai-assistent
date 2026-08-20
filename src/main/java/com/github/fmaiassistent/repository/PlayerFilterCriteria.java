package com.github.fmaiassistent.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PlayerFilterCriteria(
        String name, String gender, String playingNation, String playingCompetition, String club,
        Integer ageMin, Integer ageMax, Integer heightMin, Integer heightMax, String nationality,
        Integer currentReputationMin, Integer currentReputationMax, Integer homeReputationMin,
        Integer homeReputationMax, Integer worldReputationMin, Integer worldReputationMax,
        Integer caMin, Integer caMax, Integer paMin, Integer paMax,
        LocalDate contractEndDateFrom, LocalDate contractEndDateTo,
        Long askingPriceMin, Long askingPriceMax, Long salaryMax,
        Map<String, Integer> positionMinimums, Map<String, Integer> attributeMinimums,
        Advanced advanced) {

    /** Backward-compatible constructor for existing base-filter callers. */
    public PlayerFilterCriteria(
            String name, String gender, String playingNation, String playingCompetition, String club,
            Integer ageMin, Integer ageMax, Integer heightMin, Integer heightMax, String nationality,
            Integer currentReputationMin, Integer currentReputationMax, Integer homeReputationMin,
            Integer homeReputationMax, Integer worldReputationMin, Integer worldReputationMax,
            Integer caMin, Integer caMax, Integer paMin, Integer paMax,
            LocalDate contractEndDateFrom, LocalDate contractEndDateTo,
            Long askingPriceMin, Long askingPriceMax, Long salaryMax,
            Map<String, Integer> positionMinimums, Map<String, Integer> attributeMinimums) {
        this(name, gender, playingNation, playingCompetition, club, ageMin, ageMax, heightMin, heightMax,
                nationality, currentReputationMin, currentReputationMax, homeReputationMin, homeReputationMax,
                worldReputationMin, worldReputationMax, caMin, caMax, paMin, paMax,
                contractEndDateFrom, contractEndDateTo, askingPriceMin, askingPriceMax, salaryMax,
                positionMinimums, attributeMinimums, Advanced.empty());
    }

    public PlayerFilterCriteria {
        positionMinimums = positionMinimums == null ? Map.of() : Map.copyOf(positionMinimums);
        attributeMinimums = attributeMinimums == null ? Map.of() : Map.copyOf(attributeMinimums);
        advanced = advanced == null ? Advanced.empty() : advanced;
    }

    public static PlayerFilterCriteria clubOnly(String club) {
        return empty().withClub(club);
    }

    public PlayerFilterCriteria withClub(String club) {
        return new PlayerFilterCriteria(name, gender, playingNation, playingCompetition, club == null ? "" : club,
                ageMin, ageMax, heightMin, heightMax, nationality, currentReputationMin, currentReputationMax,
                homeReputationMin, homeReputationMax, worldReputationMin, worldReputationMax,
                caMin, caMax, paMin, paMax, contractEndDateFrom, contractEndDateTo,
                askingPriceMin, askingPriceMax, salaryMax, positionMinimums, attributeMinimums, advanced);
    }

    public PlayerFilterCriteria withName(String value) {
        return new PlayerFilterCriteria(value, gender, playingNation, playingCompetition, club,
                ageMin, ageMax, heightMin, heightMax, nationality,
                currentReputationMin, currentReputationMax, homeReputationMin, homeReputationMax,
                worldReputationMin, worldReputationMax, caMin, caMax, paMin, paMax,
                contractEndDateFrom, contractEndDateTo, askingPriceMin, askingPriceMax, salaryMax,
                positionMinimums, attributeMinimums, advanced);
    }

    public PlayerFilterCriteria withAdvanced(Advanced advanced) {
        return new PlayerFilterCriteria(name, gender, playingNation, playingCompetition, club,
                ageMin, ageMax, heightMin, heightMax, nationality, currentReputationMin, currentReputationMax,
                homeReputationMin, homeReputationMax, worldReputationMin, worldReputationMax,
                caMin, caMax, paMin, paMax, contractEndDateFrom, contractEndDateTo,
                askingPriceMin, askingPriceMax, salaryMax, positionMinimums, attributeMinimums, advanced);
    }

    public PlayerFilterCriteria withContractEndDateRange(LocalDate from, LocalDate to) {
        return new PlayerFilterCriteria(name, gender, playingNation, playingCompetition, club,
                ageMin, ageMax, heightMin, heightMax, nationality, currentReputationMin, currentReputationMax,
                homeReputationMin, homeReputationMax, worldReputationMin, worldReputationMax,
                caMin, caMax, paMin, paMax, from, to, askingPriceMin, askingPriceMax, salaryMax,
                positionMinimums, attributeMinimums, advanced);
    }

    public static PlayerFilterCriteria empty() {
        return new PlayerFilterCriteria("", "", "", "", "", null, null, null, null, "",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, Map.of(), Map.of(), Advanced.empty());
    }

    public boolean isClubOnly() {
        return !blank(club) && baseEmptyExceptClub() && advanced.isEmpty();
    }

    private boolean baseEmptyExceptClub() {
        return blank(name) && blank(gender) && blank(playingNation) && blank(playingCompetition)
                && ageMin == null && ageMax == null && heightMin == null && heightMax == null
                && blank(nationality) && currentReputationMin == null && currentReputationMax == null
                && homeReputationMin == null && homeReputationMax == null
                && worldReputationMin == null && worldReputationMax == null
                && caMin == null && caMax == null && paMin == null && paMax == null
                && contractEndDateFrom == null && contractEndDateTo == null
                && askingPriceMin == null && askingPriceMax == null && salaryMax == null
                && positionMinimums.isEmpty() && attributeMinimums.isEmpty();
    }

    public boolean isEmpty() {
        return blank(name) && blank(gender) && blank(playingNation) && blank(playingCompetition)
                && blank(club) && baseEmptyExceptClub() && advanced.isEmpty();
    }

    public String chatSummary() {
        List<String> parts = new ArrayList<>();
        add(parts, "name", name); add(parts, "gender", gender); add(parts, "nation", playingNation);
        add(parts, "competition", playingCompetition); add(parts, "club", club); add(parts, "nationality", nationality);
        addRange(parts, "age", ageMin, ageMax); addRange(parts, "CA", caMin, caMax); addRange(parts, "PA", paMin, paMax);
        if (contractEndDateFrom != null || contractEndDateTo != null) {
            parts.add("contract " + (contractEndDateFrom == null ? "≤ " + contractEndDateTo
                    : contractEndDateTo == null ? "≥ " + contractEndDateFrom
                    : contractEndDateFrom + "–" + contractEndDateTo));
        }
        if (askingPriceMax != null) parts.add("fee ≤ " + askingPriceMax);
        if (salaryMax != null) parts.add("wage ≤ " + salaryMax);
        if (!positionMinimums.isEmpty()) parts.add("positions " + positionMinimums);
        if (!attributeMinimums.isEmpty()) parts.add("attributes " + attributeMinimums.size());
        parts.addAll(advanced.summaryParts());
        return String.join(", ", parts);
    }

    private static void add(List<String> parts, String label, String value) {
        if (!blank(value)) parts.add(label + " " + value.strip());
    }

    private static void addRange(List<String> parts, String label, Integer min, Integer max) {
        if (min != null || max != null) {
            parts.add(label + " " + (min == null ? "≤ " + max : max == null ? "≥ " + min : min + "–" + max));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public enum LoanStatus { ANY, LOANED, NOT_LOANED }

    public enum ClubScope { EITHER, CONTRACTED, PLAYING }

    public record Advanced(
            Boolean injured, Boolean transferListed, Boolean listedForLoan, Boolean transferAgreed,
            Boolean freeAgent, LoanStatus loanStatus, ClubScope clubScope,
            Integer appearancesMin, Integer appearancesMax, Integer startsMin, Integer startsMax,
            Integer minutesMin, Integer minutesMax, Integer goalsMin, Integer goalsMax,
            Integer assistsMin, Integer assistsMax, Double averageRatingMin, Double averageRatingMax) {

        public Advanced {
            loanStatus = loanStatus == null ? LoanStatus.ANY : loanStatus;
            clubScope = clubScope == null ? ClubScope.EITHER : clubScope;
        }

        public static Advanced empty() {
            return new Advanced(null, null, null, null, null, LoanStatus.ANY, ClubScope.EITHER,
                    null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public boolean isEmpty() {
            return injured == null && transferListed == null && listedForLoan == null && transferAgreed == null
                    && freeAgent == null && loanStatus == LoanStatus.ANY && clubScope == ClubScope.EITHER
                    && appearancesMin == null && appearancesMax == null && startsMin == null && startsMax == null
                    && minutesMin == null && minutesMax == null && goalsMin == null && goalsMax == null
                    && assistsMin == null && assistsMax == null && averageRatingMin == null && averageRatingMax == null;
        }

        private List<String> summaryParts() {
            List<String> parts = new ArrayList<>();
            if (injured != null) parts.add(injured ? "injured" : "fit");
            if (transferListed != null) parts.add(transferListed ? "transfer-listed" : "not-transfer-listed");
            if (listedForLoan != null) parts.add(listedForLoan ? "loan-listed" : "not-loan-listed");
            if (transferAgreed != null) parts.add(transferAgreed ? "future-transfer" : "no-future-transfer");
            if (freeAgent != null) parts.add(freeAgent ? "free-agent" : "contracted");
            if (loanStatus != LoanStatus.ANY) parts.add("loan " + loanStatus.name().toLowerCase());
            if (clubScope != ClubScope.EITHER) parts.add("clubScope " + clubScope.name());
            addRange(parts, "apps", appearancesMin, appearancesMax); addRange(parts, "starts", startsMin, startsMax);
            addRange(parts, "minutes", minutesMin, minutesMax); addRange(parts, "goals", goalsMin, goalsMax);
            addRange(parts, "assists", assistsMin, assistsMax);
            if (averageRatingMin != null || averageRatingMax != null) {
                parts.add("rating " + (averageRatingMin == null ? "≤ " + averageRatingMax
                        : averageRatingMax == null ? "≥ " + averageRatingMin
                        : averageRatingMin + "–" + averageRatingMax));
            }
            return parts;
        }

        private static void addRange(List<String> parts, String label, Integer min, Integer max) {
            if (min != null || max != null) {
                parts.add(label + " " + (min == null ? "≤ " + max : max == null ? "≥ " + min : min + "–" + max));
            }
        }
    }
}
