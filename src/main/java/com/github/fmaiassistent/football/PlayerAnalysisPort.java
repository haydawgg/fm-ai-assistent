package com.github.fmaiassistent.football;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.mcp.SquadAdvice;

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
    Map<String, Object> compareSquads(String leftClub, String rightClub);

    List<Map<String, Object>> unavailableForClub(String managingClub);

    PlayerEntity playerByName(String name);

    List<SquadAdvice.AcademyRow> academyRows(String managingClub, Integer maxAge);

    List<SquadAdvice.XiPick> bestXiRows(String managingClub, List<SquadAdvice.XiSlot> slots);

    List<Map<String, Object>> suggestedBuys(String managingClub, List<SquadAdvice.XiPick> picks);

}
