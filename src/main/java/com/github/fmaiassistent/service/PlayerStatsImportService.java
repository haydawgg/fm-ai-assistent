package com.github.fmaiassistent.service;

import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.domain.entity.PlayerImportedStatEntity;
import com.github.fmaiassistent.domain.entity.PlayerMatchStatEntity;
import com.github.fmaiassistent.domain.entity.PlayerStatsImportHistoryEntity;
import com.github.fmaiassistent.exporter.PlayerStatsCsvImporter;
import com.github.fmaiassistent.repository.LoadMetadataRepository;
import com.github.fmaiassistent.repository.PlayerImportedStatRepository;
import com.github.fmaiassistent.repository.PlayerMatchStatRepository;
import com.github.fmaiassistent.repository.PlayerRepository;
import com.github.fmaiassistent.repository.PlayerStatsImportHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/** Imports UI/plugin CSV data without replacing unavailable native values with zeroes. */
@Service
public class PlayerStatsImportService {
    private final PlayerRepository players;
    private final PlayerImportedStatRepository importedStats;
    private final PlayerMatchStatRepository matchStats;
    private final LoadMetadataRepository metadata;
    private final PlayerStatsImportHistoryRepository history;

    public PlayerStatsImportService(PlayerRepository players, PlayerImportedStatRepository importedStats,
                                    PlayerMatchStatRepository matchStats, LoadMetadataRepository metadata) {
        this(players, importedStats, matchStats, metadata, null);
    }

    @Autowired
    public PlayerStatsImportService(PlayerRepository players, PlayerImportedStatRepository importedStats,
                                    PlayerMatchStatRepository matchStats, LoadMetadataRepository metadata,
                                    PlayerStatsImportHistoryRepository history) {
        this.players = players;
        this.importedStats = importedStats;
        this.matchStats = matchStats;
        this.metadata = metadata;
        this.history = history;
    }

    @Transactional
    public ImportResult importCsv(InputStream input, String sourceName) throws IOException {
        return importBytes(input.readAllBytes(), sourceName);
    }

    public ImportPreview preview(byte[] contents, String sourceName) throws IOException {
        String source = sourceName == null || sourceName.isBlank() ? "csv-import" : sourceName.trim();
        String fallbackSeason = metadata.findById("season_key").map(LoadMetadataEntity::getValue).orElse("");
        PlayerStatsCsvImporter.ImportBatch batch = parse(contents, fallbackSeason);
        List<RowPreview> rows = new ArrayList<>();
        int rowNumber = 1;
        for (PlayerStatsCsvImporter.Row row : batch.rows()) {
            List<PlayerEntity> candidates = row.valid() ? candidates(row) : List.of();
            MatchStatus status = !row.valid() ? MatchStatus.INVALID
                    : candidates.size() == 1 ? MatchStatus.MATCHED
                    : candidates.isEmpty() ? MatchStatus.UNMATCHED : MatchStatus.AMBIGUOUS;
            PlayerEntity player = candidates.size() == 1 ? candidates.getFirst() : null;
            String playingClub = player == null ? "" : safe(player.getPlayingClub());
            String contractedClub = player == null ? "" : safe(player.getClub());
            rows.add(new RowPreview(rowNumber++, row.name(), row.club(), status,
                    player == null ? null : player.getId(), player == null ? "" : player.getName(),
                    playingClub.isBlank() ? contractedClub : playingClub,
                    row.issues()));
        }
        return new ImportPreview(contents.clone(), source, batch, List.copyOf(rows));
    }

    @Transactional
    public ImportResult importPreview(ImportPreview preview) throws IOException {
        return importPreview(preview, preview == null ? "" : preview.batch().season(),
                preview == null ? "all_competitions" : preview.batch().scope());
    }

    @Transactional
    public ImportResult importPreview(ImportPreview preview, String season, String scope) throws IOException {
        if (preview == null) throw new IllegalArgumentException("Import preview is required");
        String effectiveSeason = season == null || season.isBlank() ? preview.batch().season() : season.trim();
        String effectiveScope = scope == null || scope.isBlank() ? preview.batch().scope() : scope.trim();
        PlayerStatsCsvImporter.ImportBatch batch = new PlayerStatsCsvImporter.ImportBatch(
                effectiveSeason, effectiveScope, preview.batch().rows(), preview.batch().skippedRows(), preview.batch().invalidRows());
        return importBatch(batch, preview.source());
    }

    private ImportResult importBytes(byte[] contents, String sourceName) throws IOException {
        String source = sourceName == null || sourceName.isBlank() ? "csv-import" : sourceName.trim();
        String fallbackSeason = metadata.findById("season_key").map(LoadMetadataEntity::getValue).orElse("");
        return importBatch(parse(contents, fallbackSeason), source);
    }

    private PlayerStatsCsvImporter.ImportBatch parse(byte[] contents, String fallbackSeason) throws IOException {
        return PlayerStatsCsvImporter.parse(
                new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contents), StandardCharsets.UTF_8)),
                fallbackSeason, "all_competitions");
    }

    private ImportResult importBatch(PlayerStatsCsvImporter.ImportBatch batch, String source) {
        OffsetDateTime importedAt = OffsetDateTime.now();
        importedStats.deleteBySourceAndSeasonKeyAndStatsScope(source, batch.season(), batch.scope());
        matchStats.deleteBySourceAndSeasonKey(source, batch.season());
        List<PlayerImportedStatEntity> importedRows = new ArrayList<>();
        List<PlayerMatchStatEntity> matchRows = new ArrayList<>();
        List<PlayerEntity> changed = new ArrayList<>();
        int unmatched = 0;
        int invalid = 0;
        for (PlayerStatsCsvImporter.Row row : batch.rows()) {
            if (!row.valid()) {
                invalid++;
                continue;
            }
            Optional<PlayerEntity> match = uniqueMatch(row);
            if (match.isEmpty()) {
                unmatched++;
                continue;
            }
            PlayerEntity player = match.get();
            if (!row.hasMatchContext()) {
                player.applyImportedSeasonStats(row.appearances(), row.starts(), row.minutes(), row.goals(),
                        row.assists(), row.averageRating());
            }
            changed.add(player);
            row.extras().forEach((name, value) -> {
                if (row.hasMatchContext()) {
                    matchRows.add(new PlayerMatchStatEntity(player, batch.season(), row.matchDate(),
                            safe(row.competition()), safe(row.opponent()), name, value, source, importedAt));
                } else {
                    importedRows.add(new PlayerImportedStatEntity(player, batch.season(), batch.scope(), name,
                            value, source, importedAt));
                }
            });
            if (row.hasMatchContext()) {
                Map<String, Double> core = coreStats(row);
                core.forEach((name, value) -> matchRows.add(new PlayerMatchStatEntity(player, batch.season(),
                        row.matchDate(), safe(row.competition()), safe(row.opponent()), name, value, source, importedAt)));
            } else {
                addCoreImported(importedRows, player, batch, row, source, importedAt);
            }
        }
        players.saveAll(changed.stream().distinct().toList());
        importedStats.saveAll(importedRows);
        matchStats.saveAll(matchRows);
        metadata.save(new LoadMetadataEntity("season_stats_import_source", source));
        metadata.save(new LoadMetadataEntity("season_stats_imported_at", importedAt.toString()));
        metadata.save(new LoadMetadataEntity("season_stats_import_matched", String.valueOf(batch.rows().size() - unmatched - invalid)));
        metadata.save(new LoadMetadataEntity("season_stats_import_unmatched", String.valueOf(unmatched)));
        metadata.save(new LoadMetadataEntity("season_stats_source", "csv"));
        int matchedRows = batch.rows().size() - unmatched - invalid;
        String state = matchedRows == 0 ? "unavailable"
                : (invalid > 0 || unmatched > 0 ? "partial" : "available");
        metadata.save(new LoadMetadataEntity("season_stats_state", state));
        metadata.save(new LoadMetadataEntity("season_stats_available", String.valueOf(matchedRows > 0)));
        ImportResult result = new ImportResult(batch.season(), batch.scope(), batch.rows().size(), batch.skippedRows() + invalid, unmatched,
                importedRows.size(), matchRows.size(), source);
        if (history != null) {
            history.save(new PlayerStatsImportHistoryEntity(importedAt, source, result.season(), result.scope(),
                    result.rows(), result.rows() - result.skippedRows() - result.unmatchedRows(),
                    result.skippedRows(), result.unmatchedRows(), result.importedStatRows(), result.matchStatRows(), "completed"));
        }
        return result;
    }

    private static void addCoreImported(List<PlayerImportedStatEntity> target, PlayerEntity player,
                                        PlayerStatsCsvImporter.ImportBatch batch, PlayerStatsCsvImporter.Row row,
                                        String source, OffsetDateTime importedAt) {
        Map<String, Double> core = new LinkedHashMap<>();
        put(core, "appearances", row.appearances());
        put(core, "starts", row.starts());
        put(core, "minutes", row.minutes());
        put(core, "goals", row.goals());
        put(core, "assists", row.assists());
        put(core, "average_rating", row.averageRating());
        core.forEach((name, value) -> target.add(new PlayerImportedStatEntity(player, batch.season(), batch.scope(), name,
                value, source, importedAt)));
    }

    private static Map<String, Double> coreStats(PlayerStatsCsvImporter.Row row) {
        Map<String, Double> core = new LinkedHashMap<>();
        put(core, "appearances", row.appearances());
        put(core, "starts", row.starts());
        put(core, "minutes", row.minutes());
        put(core, "goals", row.goals());
        put(core, "assists", row.assists());
        put(core, "average_rating", row.averageRating());
        return core;
    }

    private static void put(Map<String, Double> target, String key, Number value) {
        if (value != null) target.put(key, value.doubleValue());
    }

    private Optional<PlayerEntity> uniqueMatch(PlayerStatsCsvImporter.Row row) {
        List<PlayerEntity> candidates = candidates(row);
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private List<PlayerEntity> candidates(PlayerStatsCsvImporter.Row row) {
        List<PlayerEntity> players = this.players.findByNameContainingIgnoreCase(row.name());
        String wantedName = normalize(row.name());
        String wantedClub = normalize(row.club());
        List<PlayerEntity> candidates = players.stream().filter(player -> normalize(player.getName()).equals(wantedName)).toList();
        if (!wantedClub.isBlank()) {
            candidates = candidates.stream().filter(player -> normalize(player.getClub()).equals(wantedClub)
                    || normalize(player.getPlayingClub()).equals(wantedClub)).toList();
        }
        return candidates;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record ImportResult(String season, String scope, int rows, int skippedRows, int unmatchedRows,
                               int importedStatRows, int matchStatRows, String source) {
    }

    @Transactional(readOnly = true)
    public List<PlayerStatsImportHistoryEntity> history() {
        return history == null ? List.of() : history.findTop20ByOrderByImportedAtDesc();
    }

    public record ImportPreview(byte[] contents, String source, PlayerStatsCsvImporter.ImportBatch batch,
                                List<RowPreview> rows) {
        public ImportPreview {
            contents = contents == null ? new byte[0] : contents.clone();
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record RowPreview(int rowNumber, String importedName, String importedClub, MatchStatus status,
                             Long matchedPlayerId, String matchedPlayerName, String matchedClub,
                             List<String> issues) {
        public RowPreview {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public enum MatchStatus { MATCHED, AMBIGUOUS, UNMATCHED, INVALID }
}
