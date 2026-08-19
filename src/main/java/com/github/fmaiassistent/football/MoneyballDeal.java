package com.github.fmaiassistent.football;

/** Transport-free market comparison attached to a moneyball candidate. */
public record MoneyballDeal(
        double score,
        String tier,
        long marketPrice,
        long marketWage,
        int marketSamples,
        long totalCost,
        long marketCost) {
}
