package com.github.fmaiassistent.service;

import com.github.fmaiassistent.config.JCacheConfiguration;
import com.github.fmaiassistent.exporter.ClubExporter;
import com.github.fmaiassistent.exporter.CompetitionExporter;
import com.github.fmaiassistent.exporter.PlayerExporter;
import com.github.fmaiassistent.repository.DatabaseService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

@Service
public class SnapshotPersistService {
    private final DatabaseService databaseService;
    private final CompetitionDatabaseService competitions;
    private final ClubDatabaseService clubs;
    private final PlayerDatabaseService players;

    public SnapshotPersistService(
            DatabaseService databaseService,
            CompetitionDatabaseService competitions,
            ClubDatabaseService clubs,
            PlayerDatabaseService players) {
        this.databaseService = databaseService;
        this.competitions = competitions;
        this.clubs = clubs;
        this.players = players;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = JCacheConfiguration.PLAYERS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = JCacheConfiguration.PLAYERS_WITH_CLUBS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = JCacheConfiguration.NATIONS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = JCacheConfiguration.COMPETITIONS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = JCacheConfiguration.CLUB_NAMES_CACHE, allEntries = true),
            @CacheEvict(cacheNames = JCacheConfiguration.CLUB_CACHE, allEntries = true),
            @CacheEvict(cacheNames = JCacheConfiguration.PLAYER_MAPPING_CACHE, allEntries = true)
    })
    @Transactional
    public DatabaseLoadAllService.LoadAllResult persist(
            int pid,
            CompetitionExporter.ExportResult competitionRows,
            ClubExporter.ExportResult clubRows,
            PlayerExporter.ExportResult playerRows,
            Consumer<LoadProgress> progress) {
        LoadProgressReporter reporter = new LoadProgressReporter(progress);
        long playerTotal = playerRows.rows().size();
        reporter.start(LoadProgress.Phase.SAVING, playerTotal);
        databaseService.clearAllTables();
        competitions.saveExported(competitionRows);
        reporter.report(new LoadProgress(LoadProgress.Phase.SAVING, 0, playerTotal, 0, " · competitions saved"));
        clubs.saveExported(clubRows);
        reporter.report(new LoadProgress(LoadProgress.Phase.SAVING, 0, playerTotal, 0, " · clubs saved"));
        PlayerDatabaseService.LoadResult playerResult = players.saveExported(playerRows, progress);
        return new DatabaseLoadAllService.LoadAllResult(
                pid,
                playerResult.gameDate(),
                playerResult.count(),
                clubs.countClubs(),
                competitions.countCompetitions());
    }
}
