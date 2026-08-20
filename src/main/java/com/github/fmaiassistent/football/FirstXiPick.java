package com.github.fmaiassistent.football;

/** Domain first-XI selection result with explicit hole semantics. */
public record FirstXiPick(
        String position,
        String inPossessionRole,
        String outOfPossessionRole,
        String playerName,
        int positionScore,
        Double roleFit,
        int ca,
        int pa,
        boolean hole) {
}
