package com.github.fmaiassistent.football;

/**
 * Domain query for the typed recruitment workspace.
 *
 * <p>The query contains only analysis concerns. MCP annotations, JSON names,
 * and transport defaults stay in the adapter that accepts this query.</p>
 */
public record TransferShortlistQuery(
        String managingClub,
        String position,
        String roleName,
        Integer maxAge,
        Integer minCurrentAbility,
        Integer minPotentialAbility,
        Long maxAskingPrice,
        Integer maxWeeklySalary) {
}
