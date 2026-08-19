package com.github.fmaiassistent.football;

import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.mcp.FmAiAssistentTools;
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

    List<SquadAdvice.SellRow> sellRows(String managingClub);

    List<SquadAdvice.ContractRow> contractRows(String managingClub);

    SquadAdvice.WageHealth wageHealth(String managingClub);

    List<Map<String, Object>> unavailableForClub(String managingClub);

    PlayerEntity playerByName(String name);

    List<SquadAdvice.AcademyRow> academyRows(String managingClub, Integer maxAge);

    List<FmAiAssistentTools.TransferShortlistRow> transferShortlistRows(
            String managingClub,
            String position,
            String roleName,
            Integer maxAge,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Long maxAskingPrice,
            Integer maxWeeklySalary);

    List<SquadAdvice.XiPick> bestXiRows(String managingClub, List<SquadAdvice.XiSlot> slots);

    List<Map<String, Object>> suggestedBuys(String managingClub, List<SquadAdvice.XiPick> picks);

    FmAiAssistentTools.MoneyballResult moneyballRows(
            String managingClub,
            String position,
            Integer minCurrentAbility,
            Integer minPotentialAbility,
            Integer maxAge,
            Long maxAskingPrice,
            Integer maxWeeklySalary);
}
