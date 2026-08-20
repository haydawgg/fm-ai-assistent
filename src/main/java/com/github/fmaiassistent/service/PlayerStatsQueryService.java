package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.domain.entity.PlayerImportedStatEntity;
import com.github.fmaiassistent.domain.entity.PlayerMatchStatEntity;
import com.github.fmaiassistent.repository.LoadMetadataRepository;
import com.github.fmaiassistent.repository.PlayerImportedStatRepository;
import com.github.fmaiassistent.repository.PlayerMatchStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PlayerStatsQueryService {
    private final PlayerImportedStatRepository importedStats;
    private final PlayerMatchStatRepository matchStats;
    private final LoadMetadataRepository metadata;

    public PlayerStatsQueryService(PlayerImportedStatRepository importedStats,
                                   PlayerMatchStatRepository matchStats,
                                   LoadMetadataRepository metadata) {
        this.importedStats = importedStats;
        this.matchStats = matchStats;
        this.metadata = metadata;
    }

    @Transactional(readOnly = true)
    public Map<String, Double> importedStats(PlayerEntity player) {
        if (player == null || player.getId() == null) return Map.of();
        String season = metadata("season_key");
        String scope = metadata("season_stats_scope");
        Map<String, Double> out = new LinkedHashMap<>();
        for (PlayerImportedStatEntity row : importedStats.findByPlayerIdAndSeasonKeyAndStatsScopeOrderByStatName(
                player.getId(), season, scope.isBlank() ? "all_competitions" : scope)) {
            out.put(row.getStatName(), row.getStatValue());
        }
        return Map.copyOf(out);
    }

    @Transactional(readOnly = true)
    public List<MatchSummary> recentMatches(PlayerEntity player, int limit) {
        if (player == null || player.getId() == null) return List.of();
        String season = metadata("season_key");
        Map<String, MatchBuilder> grouped = new LinkedHashMap<>();
        for (PlayerMatchStatEntity row : matchStats.findByPlayerIdAndSeasonKeyOrderByMatchDateDesc(player.getId(), season)) {
            String key = row.getMatchDate() + "\u0000" + row.getCompetition() + "\u0000" + row.getOpponent();
            grouped.computeIfAbsent(key, ignored -> new MatchBuilder(row)).stats.put(row.getStatName(), row.getStatValue());
        }
        return grouped.values().stream().limit(Math.max(1, Math.min(50, limit)))
                .map(MatchBuilder::build).toList();
    }

    public String season() { return metadata("season_key"); }
    public String source() { return metadata("season_stats_source"); }

    private String metadata(String key) {
        return metadata.findById(key).map(LoadMetadataEntity::getValue).orElse("");
    }

    private static final class MatchBuilder {
        private final String date;
        private final String competition;
        private final String opponent;
        private final Map<String, Double> stats = new LinkedHashMap<>();
        private MatchBuilder(PlayerMatchStatEntity row) {
            date = row.getMatchDate(); competition = row.getCompetition(); opponent = row.getOpponent();
        }
        private MatchSummary build() { return new MatchSummary(date, competition, opponent, Map.copyOf(stats)); }
    }

    public record MatchSummary(String date, String competition, String opponent, Map<String, Double> stats) {
        public MatchSummary { stats = stats == null ? Map.of() : Map.copyOf(stats); }
    }
}
