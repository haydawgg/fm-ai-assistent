package com.github.fmaiassistent.football;

import java.util.List;

/** Transport-free squad-trim recommendation. */
public record SquadSellCandidate(
        int rank,
        String name,
        Integer age,
        String position,
        int ca,
        int pa,
        int salaryWeekly,
        Long askingPrice,
        String contractEnd,
        int depthAtPosition,
        int caVsFirstTeam,
        String recommendation,
        double sellScore,
        List<String> reasons) {
    public SquadSellCandidate {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
