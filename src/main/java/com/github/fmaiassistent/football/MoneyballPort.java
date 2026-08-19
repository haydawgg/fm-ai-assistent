package com.github.fmaiassistent.football;

/** Small domain seam for value-focused recruitment queries. */
public interface MoneyballPort {
    MoneyballAnalysisResult moneyballCandidates(MoneyballQuery query);
}
