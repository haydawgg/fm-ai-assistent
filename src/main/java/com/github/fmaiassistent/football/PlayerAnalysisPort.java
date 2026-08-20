package com.github.fmaiassistent.football;

import com.github.fmaiassistent.domain.entity.PlayerEntity;

import java.util.List;
import java.util.Map;

/**
 * Stable read seam for player analysis used by desktop views.
 *
 * <p>The MCP adapter remains responsible for tool names, argument handling,
 * and JSON-compatible result maps. Views depend on this smaller contract so
 * the analysis implementation can be tested or replaced without coupling UI
 * modules to transport concerns.</p>
 */
public interface PlayerAnalysisPort {
    default Map<String, Object> snapshotMetadata() {
        return Map.of();
    }

    default Map<String, Double> importedPlayerStats(PlayerEntity player) {
        return Map.of();
    }

    default List<Map<String, Object>> recentPlayerMatchStats(PlayerEntity player, int limit) {
        return List.of();
    }

    Map<String, Object> compareSquads(String leftClub, String rightClub);

    List<Map<String, Object>> unavailableForClub(String managingClub);

    PlayerEntity playerByName(String name);

    List<AcademyCandidate> academyCandidates(String managingClub, Integer maxAge);

    List<FirstXiPick> bestXi(String managingClub, List<FirstXiSlot> slots);

    List<Map<String, Object>> suggestedBuys(FirstXiSuggestionQuery query);
}
