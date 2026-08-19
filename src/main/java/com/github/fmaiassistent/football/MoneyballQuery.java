package com.github.fmaiassistent.football;

/** Domain query for value-focused recruitment. */
public record MoneyballQuery(
        String managingClub,
        String position,
        Integer minCurrentAbility,
        Integer minPotentialAbility,
        Integer maxAge,
        Long maxAskingPrice,
        Integer maxWeeklySalary) {
}
