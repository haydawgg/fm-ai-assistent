package com.github.fmaiassistent.football;

/** Transport-free value candidate returned by the moneyball module. */
public record MoneyballCandidate(
        int rank,
        String name,
        Integer age,
        String nationality,
        String club,
        int positionScore,
        int ca,
        int pa,
        int developmentUpside,
        String ageCurve,
        String willingness,
        boolean freeAgent,
        long costFee,
        long salaryWeekly,
        MoneyballDeal deal,
        double qualityScore,
        int signingRating) {
}
