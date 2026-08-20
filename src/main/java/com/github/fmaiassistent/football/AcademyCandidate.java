package com.github.fmaiassistent.football;

/** Transport-free academy candidate for the player-analysis seam. */
public record AcademyCandidate(
        String name,
        String position,
        Integer age,
        int ca,
        int pa,
        int upside,
        int vsFirstTeam,
        int dualPositions,
        int salaryWeekly,
        String contractEnd) {
}
