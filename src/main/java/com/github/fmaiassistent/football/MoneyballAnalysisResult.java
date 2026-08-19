package com.github.fmaiassistent.football;

import java.util.List;

/** Immutable aggregate returned by the value-analysis module. */
public record MoneyballAnalysisResult(
        List<MoneyballCandidate> rows,
        int candidatePoolSize,
        int ratedCount,
        int pricedPlayers,
        int bucketCount) {
    public MoneyballAnalysisResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
