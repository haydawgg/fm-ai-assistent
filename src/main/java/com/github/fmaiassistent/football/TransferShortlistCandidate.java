package com.github.fmaiassistent.football;

import java.util.List;

/** Transport-free ranked candidate returned by the recruitment module. */
public record TransferShortlistCandidate(
        int rank,
        double score,
        String name,
        Integer age,
        String nationality,
        String club,
        int positionScore,
        Double roleFit,
        int ca,
        int pa,
        int developmentUpside,
        Long askingPrice,
        int salaryWeekly,
        String willingness,
        boolean freeAgent,
        boolean transferListed,
        boolean injured,
        List<String> signals) {
    public TransferShortlistCandidate {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
